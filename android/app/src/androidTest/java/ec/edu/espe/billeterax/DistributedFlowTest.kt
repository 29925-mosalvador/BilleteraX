package ec.edu.espe.billeterax
import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ec.edu.espe.billeterax.data.*
import ec.edu.espe.billeterax.model.*
import ec.edu.espe.billeterax.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Test
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID

/** Override serverUrl/tokenA/tokenB for Debian. Uses a separate on-device database. */
@RunWith(AndroidJUnit4::class)
class DistributedFlowTest {
    @Test fun retrofitServerReceiptRoomFlowAndWebSocket() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val url = (args.getString("serverUrl") ?: "http://10.0.2.2:8080/").trimEnd('/') + "/"
        val localDemo = url == "http://10.0.2.2:8080/"
        val tokenA = args.getString("tokenA") ?: if (localDemo) "demo-cta001-local-only" else ""
        val tokenB = args.getString("tokenB") ?: if (localDemo) "demo-cta002-local-only" else ""
        require(tokenA.isNotBlank() && tokenB.isNotBlank()) {
            "Indicar tokenA y tokenB para el servidor elegido; no guardar credenciales en las fuentes."
        }
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "distributed-flow-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, WalletDatabase::class.java, name).build()
        val config = Config(url, "CTA001", tokenA)
        val repo = WalletRepository(db, config)
        val sender = WalletNetwork(Config(config.url, "CTA002", tokenB))
        val session = launch { repo.runSession() }
        try {
            withTimeout(20_000) { repo.connection.first { it == Connection.CONNECTED } }
            repo.synchronize()
            val initial = repo.balance.first { it != null }!!.cents
            val receipt = repo.execute(Operation.DEPOSIT, "1.23", null, null, null)
            assertEquals(initial + 123, repo.balance.first { it?.cents == initial + 123 }!!.cents)
            assertTrue(repo.transactions.first().any { it.id == receipt.transaction.id })
            // Ensure B can send $20 even when this test is run more than once.
            sender.api.execute("transactions/deposit", MoneyRequest(UUID.randomUUID().toString(), "CTA002", BigDecimal("20.00")))
            val received = sender.api.execute("transactions/transfer", MoneyRequest(UUID.randomUUID().toString(), "CTA002", BigDecimal("20.00"), "CTA001"))
            withTimeout(15_000) {
                repo.transactions.first { rows -> rows.any { it.operationId == received.operationId && it.type == "TRANSFER_IN" } }
                repo.balance.first { it?.cents == initial + 2123 }
            }
            repo.synchronize(); repo.synchronize()
            val history = repo.transactions.first()
            assertEquals(history.size, history.map { it.id }.toSet().size)
            assertNull(repo.pending.first())
            if (args.getString("configureApp") == "true") {
                ConfigStore(context).save(config)
            }
        } finally { session.cancelAndJoin(); repo.close(); sender.close(); db.close(); context.deleteDatabase(name) }
    }
}
