package ec.edu.espe.billeterax.network


import ec.edu.espe.billeterax.model.Connection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.*
import okhttp3.*
import kotlin.random.Random

sealed interface SocketSignal {
    data class State(val value: Connection) : SocketSignal
    data object Changed : SocketSignal
}
class WalletSocket(private val network: WalletNetwork) {
    private fun once(): Flow<SocketSignal> = callbackFlow {
        val url = network.config.url.replaceFirst("https://", "wss://").replaceFirst("http://", "ws://") + "ws"
        val socket = network.client.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) { trySend(SocketSignal.State(Connection.CONNECTED)); trySend(SocketSignal.Changed) }
            override fun onMessage(webSocket: WebSocket, text: String) {
                runCatching { network.gson.fromJson(text, EventDto::class.java) }.getOrNull()?.let {
                    if (it.accountId == network.config.account) {
                        trySend(SocketSignal.Changed)
                        if (it.type == "SESSION_REVOKED" || it.type == "SERVER_UNAVAILABLE") {
                            webSocket.close(1000, "Volver a conectar")
                            close()
                        }
                    }
                }
            }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { close() }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason); close() }
            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) { close() }
        })
        awaitClose { socket.cancel() }
    }
    fun signals(): Flow<SocketSignal> = flow {
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            emit(SocketSignal.State(if (attempt == 0) Connection.CONNECTING else Connection.RECONNECTING))
            val started = System.nanoTime()
            emitAll(once())
            emit(SocketSignal.State(Connection.RECONNECTING))
            if ((System.nanoTime() - started) / 1_000_000_000 > 30) attempt = 0
            delay((1000L shl attempt.coerceAtMost(5)).coerceAtMost(30_000L) + Random.nextLong(250))
            attempt++
        }
    }.flowOn(Dispatchers.IO)
}
