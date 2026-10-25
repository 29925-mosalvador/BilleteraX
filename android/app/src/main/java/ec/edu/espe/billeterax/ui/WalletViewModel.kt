package ec.edu.espe.billeterax.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.room.Room
import ec.edu.espe.billeterax.BuildConfig
import ec.edu.espe.billeterax.data.*
import ec.edu.espe.billeterax.model.*
import ec.edu.espe.billeterax.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import retrofit2.HttpException
import java.io.IOException

data class WalletState(val config: Config = Config(), val ready: Boolean = false, val balance: LocalBalance? = null,
    val transactions: List<LocalTransaction> = emptyList(), val pending: PendingOperation? = null,
    val connection: Connection = Connection.OFFLINE, val fresh: Boolean = false, val busy: Boolean = false,
    val message: String? = null, val successful: Boolean = false, val connectionError: String? = null,
    val user: UserProfile? = null, val appearance: Appearance = Appearance.SYSTEM)

class WalletViewModel(app: Application) : AndroidViewModel(app) {
    private val db = Room.databaseBuilder(app, WalletDatabase::class.java, "billeterax.db").build()
    private val store = ConfigStore(app)
    private var repo: WalletRepository? = null
    private var collectors: Job? = null
    private var connectionJob: Job? = null
    private var foreground = false
    private val _state = MutableStateFlow(WalletState())
    val state: StateFlow<WalletState> = _state.asStateFlow()
    init { viewModelScope.launch {
        try {
            val loaded = withContext(Dispatchers.IO) { Triple(store.load(), store.profile(), store.appearance()) }
            _state.value = WalletState(config = loaded.first, user = loaded.second, appearance = loaded.third, ready = true)
            if (loaded.second != null && loaded.first.token.isNotBlank()) configure(loaded.first, loaded.second!!)
        } catch (e: CancellationException) { throw e }
        catch (e: Exception) { _state.update { it.copy(ready = true, message = "Revisa la conexión e inicia sesión") } }
    } }
    private suspend fun disconnect() {
        connectionJob?.cancelAndJoin(); collectors?.cancelAndJoin()
        withContext(Dispatchers.IO) { repo?.close() }; repo = null
    }
    private suspend fun configure(config: Config, user: UserProfile) {
        disconnect()
        val next = WalletRepository(db, config); repo = next
        _state.value = WalletState(config = config, user = user, ready = true, appearance = _state.value.appearance)
        collectors = viewModelScope.launch {
            launch { next.balance.collect { v -> _state.update { it.copy(balance = v) } } }
            launch { next.transactions.collect { v -> _state.update { it.copy(transactions = v) } } }
            launch { next.pending.collect { v -> _state.update { it.copy(pending = v) } } }
            launch { next.connection.collect { v -> _state.update { it.copy(connection = v) } } }
            launch { next.fresh.collect { v -> _state.update { it.copy(fresh = v) } } }
            launch { next.connectionError.collect { v -> _state.update { it.copy(connectionError = v) } } }
            launch { next.sessionExpired.first { it }; viewModelScope.launch { endSession("Tu sesión ha vencido. Ingresa de nuevo") } }
        }
        if (foreground) connect()
    }
    private fun connect() { if (repo != null && connectionJob?.isActive != true) connectionJob = viewModelScope.launch { repo?.runSession() } }
    fun started() { foreground = true; connect() }
    fun stopped() { foreground = false; connectionJob?.cancel() }
    fun refresh() { repo?.refresh() }
    private fun server(raw: String): String {
        val parsed = raw.trim().toHttpUrlOrNull() ?: error("Ingresa una dirección de servidor válida")
        require(parsed.encodedPath == "/" && parsed.query == null && parsed.fragment == null && parsed.username.isEmpty() && parsed.password.isEmpty()) { "Usa solo protocolo, servidor y puerto" }
        require(BuildConfig.DEBUG || parsed.isHttps) { "El servidor debe utilizar HTTPS" }
        return parsed.toString()
    }
    fun saveServer(raw: String) {
        if (_state.value.busy || _state.value.user != null) return
        viewModelScope.launch {
            try {
                val url = server(raw); withContext(Dispatchers.IO) { store.saveServer(url) }
                _state.update { it.copy(config = Config(url), message = null) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = e.message, successful = false) } }
        }
    }
    fun authenticate(register: Boolean, name: String, username: String, password: String) {
        if (_state.value.busy || !_state.value.ready) return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val url = server(_state.value.config.url)
                val result = withContext(Dispatchers.IO) {
                    val network = WalletNetwork(Config(url))
                    try {
                        if (register) network.api.register(RegistrationRequest(name.trim(), username.trim(), password))
                        else network.api.login(LoginRequest(username.trim(), password))
                    } finally { network.close() }
                }
                val config = Config(url, result.user.accountId, result.token)
                withContext(Dispatchers.IO) { store.save(config, result.user) }
                configure(config, result.user)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { _state.update { it.copy(message = describe(e), successful = false) } }
            finally { _state.update { it.copy(busy = false) } }
        }
    }
    fun logout() {
        if (_state.value.busy) return
        _state.update { it.copy(busy = true) }
        viewModelScope.launch {
            try {
                val config = _state.value.config
                withContext(Dispatchers.IO) {
                    val network = WalletNetwork(config)
                    try { network.api.logout() } catch (e: IOException) { /* Local logout also works offline. */ }
                    finally { network.close() }
                }
            } catch (e: CancellationException) { throw e }
            catch (_: Exception) { /* Never retain the local session on logout failure. */ }
            finally { withContext(NonCancellable) { endSession("Sesión cerrada en este dispositivo") } }
        }
    }
    private suspend fun endSession(message: String) {
        disconnect(); withContext(Dispatchers.IO) { store.clearSession() }
        _state.value = WalletState(config = Config(_state.value.config.url), ready = true, appearance = _state.value.appearance,
            message = message, successful = true)
    }
    fun setAppearance(value: Appearance) {
        _state.update { it.copy(appearance = value) }
        viewModelScope.launch(Dispatchers.IO) { store.setAppearance(value) }
    }
    fun execute(op: Operation, amount: String, destination: String?, service: String?, contract: String?) = command {
        it.execute(op, amount, destination, service, contract)
    }
    fun retry() = command { it.retry() }
    private fun command(action: suspend (WalletRepository) -> ReceiptDto) {
        if (_state.value.busy || _state.value.user == null) return
        val current = repo ?: return
        _state.update { it.copy(busy = true, message = null) }
        viewModelScope.launch {
            try {
                val receipt = action(current)
                _state.update { it.copy(successful = true, message = "Operación confirmada • Comprobante ${receipt.transaction.id}") }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) {
                val message = withContext(Dispatchers.IO) { current.describe(e) }
                _state.update { it.copy(successful = false, message = message) }
            } finally { _state.update { it.copy(busy = false) } }
        }
    }
    private fun describe(e: Exception): String = when (e) {
        is HttpException -> runCatching { WalletNetworkError.message(e) }.getOrDefault("No se pudo completar la solicitud")
        is IOException -> "No pudimos conectar con el servidor. Revisa tu conexión e inténtalo de nuevo"
        else -> e.message ?: "No se pudo completar la solicitud"
    }
    fun dismissMessage() { _state.update { it.copy(message = null) } }
    override fun onCleared() {
        connectionJob?.cancel(); collectors?.cancel()
        CoroutineScope(Dispatchers.IO).launch {
            connectionJob?.join(); collectors?.join(); repo?.close(); db.close()
        }
    }
}
private object WalletNetworkError {
    fun message(e: HttpException): String = com.google.gson.Gson().fromJson(e.response()?.errorBody()?.string(), ErrorDto::class.java)?.message ?: "Error HTTP ${e.code()}"
}
