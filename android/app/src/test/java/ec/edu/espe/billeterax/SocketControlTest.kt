package ec.edu.espe.billeterax

import ec.edu.espe.billeterax.model.Config
import ec.edu.espe.billeterax.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import okhttp3.*
import okhttp3.mockwebserver.*
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

/** A proxy may delay a server close frame. A terminal notice must end the client connection. */
class SocketControlTest {
    @Test fun terminalNoticesCloseTheMatchingAccountConnection() = runBlocking {
        for (type in listOf("SESSION_REVOKED", "SERVER_UNAVAILABLE")) {
            val ended = CompletableFuture<Unit>()
            val server = MockWebServer()
            server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
                override fun onOpen(webSocket: WebSocket, response: Response) {
                    webSocket.send("""{"type":"$type","accountId":"CTA001"}""")
                }
                override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason); ended.complete(Unit) }
                override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ended.complete(Unit) }
            }))
            server.start()
            val network = WalletNetwork(Config(server.url("/").toString(), "CTA001", "test-session"))
            val job = launch { WalletSocket(network).signals().collect() }
            try {
                withContext(Dispatchers.IO) { ended.get(5, TimeUnit.SECONDS) }
                assertTrue("Client must close on $type even if the server does not initiate a close", ended.isDone)
            } finally { job.cancelAndJoin(); network.close(); server.shutdown() }
        }
    }

    @Test fun noticeForAnotherAccountCannotCloseTheCurrentConnection() = runBlocking {
        val changed = CompletableFuture<Unit>()
        val ended = CompletableFuture<Unit>()
        val count = AtomicInteger()
        val server = MockWebServer()
        server.enqueue(MockResponse().withWebSocketUpgrade(object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send("""{"type":"SESSION_REVOKED","accountId":"CTA002"}""")
                webSocket.send("""{"type":"BALANCE_CHANGED","accountId":"CTA001"}""")
            }
            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) { webSocket.close(code, reason); ended.complete(Unit) }
            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) { ended.complete(Unit) }
        }))
        server.start()
        val network = WalletNetwork(Config(server.url("/").toString(), "CTA001", "test-session"))
        val job = launch { WalletSocket(network).signals().collect { if (it == SocketSignal.Changed && count.incrementAndGet() >= 2) changed.complete(Unit) } }
        try {
            withContext(Dispatchers.IO) { changed.get(5, TimeUnit.SECONDS) }
            assertFalse("Another account's notice must not end this socket", ended.isDone)
        } finally { job.cancelAndJoin(); network.close(); server.shutdown() }
    }
}
