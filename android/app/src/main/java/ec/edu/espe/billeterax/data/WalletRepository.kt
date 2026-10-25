package ec.edu.espe.billeterax.data


import androidx.room.withTransaction
import ec.edu.espe.billeterax.model.*
import ec.edu.espe.billeterax.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import retrofit2.HttpException
import java.io.IOException
import java.time.Instant
import java.util.UUID

/** All network, Room and monetary parsing runs on IO. A mutex serializes reconciliation and commands. */
class WalletRepository(private val db: WalletDatabase, val config: Config) {
    private val network = WalletNetwork(config)
    private val dao = db.dao()
    private val mutex = Mutex()
    private val requests = Channel<Unit>(Channel.CONFLATED)
    val transactions = dao.observeTransactions(config.scope).flowOn(Dispatchers.IO)
    val balance = dao.observeBalance(config.scope).flowOn(Dispatchers.IO)
    val pending = dao.observePending(config.scope).flowOn(Dispatchers.IO)
    val connection = MutableStateFlow(Connection.OFFLINE)
    val fresh = MutableStateFlow(false)
    val sessionExpired = MutableStateFlow(false)
    val connectionError = MutableStateFlow<String?>(null)
    suspend fun runSession(): Unit = withContext(Dispatchers.IO) {
        try {
            coroutineScope {
                launch { WalletSocket(network).signals().collect { signal ->
                    when (signal) {
                        is SocketSignal.State -> { connection.value = signal.value; if (signal.value != Connection.CONNECTED) fresh.value = false }
                        SocketSignal.Changed -> requests.trySend(Unit)
                    }
                } }
                launch { for (ignored in requests) {
                    try { synchronize() } catch (e: CancellationException) { throw e }
                    catch (e: Exception) { fresh.value = false; connectionError.value = describe(e) }
                } }
                // REST also reconciles after missed events. The real-time path remains WebSocket.
                launch { while (isActive) { requests.trySend(Unit); delay(30_000) } }
            }
        } finally { connection.value = Connection.OFFLINE; fresh.value = false }
    }
    fun refresh() { requests.trySend(Unit) }
    suspend fun synchronize() = withContext(Dispatchers.IO) { mutex.withLock { syncLocked() } }
    private suspend fun syncLocked() {
        val snapshot = try { network.api.snapshot(config.account) }
        catch (e: HttpException) { if (e.code() == 401) sessionExpired.value = true; throw e }
        check(snapshot.account.accountId == config.account && snapshot.transactions.all { it.accountId == config.account })
        db.withTransaction {
            dao.upsertTransactions(snapshot.transactions.map { it.local(config.scope) })
            dao.upsertBalance(LocalBalance(config.scope, snapshot.account.ownerName, Money.fromDecimal(snapshot.account.balance), snapshot.syncedAt))
            // If an HTTP reply was lost but its receipt appears in REST, resolve the pending command.
            dao.pending(config.scope)?.let { p ->
                if (snapshot.transactions.any { it.operationId == p.operationId }) dao.clearPending(config.scope)
            }
        }
        fresh.value = true; connectionError.value = null
    }
    suspend fun execute(operation: Operation, rawAmount: String, destination: String?, service: String?, contract: String?): ReceiptDto = withContext(Dispatchers.IO) {
        mutex.withLock {
            check(dao.pending(config.scope) == null) { "Hay una operación pendiente de verificar. Reinténtala antes de crear otra" }
            val request = MoneyRequest(UUID.randomUUID().toString(), config.account, Money.decimal(Money.cents(rawAmount)),
                destination?.takeIf { operation == Operation.TRANSFER }, service?.takeIf { operation == Operation.SERVICE }, contract?.takeIf { operation == Operation.SERVICE })
            require(operation != Operation.TRANSFER || (destination?.matches(Regex("CTA[0-9]{3,12}")) == true && destination != config.account)) { "Selecciona otra cuenta destino válida" }
            require(operation != Operation.SERVICE || contract?.matches(Regex("[A-Za-z0-9-]{6,20}")) == true) { "Contrato: 6 a 20 caracteres alfanuméricos o guiones" }
            val pending = PendingOperation(config.scope, request.operationId, operation.route, network.gson.toJson(request))
            dao.insertPending(pending) // durable before sending; the same ID survives app/process restart
            sendLocked(pending)
        }
    }
    suspend fun retry(): ReceiptDto = withContext(Dispatchers.IO) { mutex.withLock {
        sendLocked(dao.pending(config.scope) ?: error("La operación ya fue conciliada; consulta el historial"))
    } }
    private suspend fun sendLocked(pending: PendingOperation): ReceiptDto {
        val request = network.gson.fromJson(pending.body, MoneyRequest::class.java)
        val receipt = try { network.api.execute(pending.route, request) }
        catch (e: HttpException) {
            if (e.code() == 401) { sessionExpired.value = true; throw e }
            if (e.code() in 400..499 && e.code() !in listOf(408, 429)) dao.clearPending(config.scope)
            throw e
        }
        catch (e: IOException) { fresh.value = false; throw e }
        check(receipt.operationId == pending.operationId && receipt.transaction.accountId == config.account)
        // Commit the confirmed receipt locally even if the following reconciliation cannot reach the server.
        db.withTransaction { dao.upsertTransactions(listOf(receipt.transaction.local(config.scope))); dao.clearPending(config.scope) }
        try { syncLocked() } catch (e: CancellationException) { throw e }
        catch (e: Exception) { fresh.value = false; connectionError.value = "Operación confirmada; saldo pendiente de sincronizar" }
        return receipt
    }
    fun describe(e: Exception): String = when (e) {
        is HttpException -> runCatching { network.gson.fromJson(e.response()?.errorBody()?.string(), ErrorDto::class.java)?.message }.getOrNull() ?: "Error HTTP ${e.code()}"
        is IOException -> "Sin respuesta del servidor. La operación no se confirma; verifica o reintenta con el mismo identificador"
        else -> e.message ?: "No se pudo completar la operación"
    }
    fun close() { network.close() }
}
