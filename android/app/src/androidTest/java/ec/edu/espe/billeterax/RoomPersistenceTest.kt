package ec.edu.espe.billeterax
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import ec.edu.espe.billeterax.data.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
@RunWith(AndroidJUnit4::class)
class RoomPersistenceTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()
    private val name = "room-persistence-test.db"
    @After fun clean() { context.deleteDatabase(name) }
    @Test fun historyAndPendingSurviveReopeningAndUpsertDoesNotDuplicate() = runBlocking(Dispatchers.IO) {
        val entry = LocalTransaction("server|CTA001", "tx1", "op1", "CTA001", "SERVICE", "Servicio Básico", -1234, 8766, "2026-10-16T12:00:00Z", null, "WATER", "123456", "CONFIRMED")
        var db = Room.databaseBuilder(context, WalletDatabase::class.java, name).build()
        db.dao().upsertTransactions(listOf(entry)); db.dao().upsertTransactions(listOf(entry))
        db.dao().upsertBalance(LocalBalance(entry.scope, "Usuario A", 8766, entry.occurredAt))
        db.dao().insertPending(PendingOperation(entry.scope,"op2","transactions/deposit","{}")); db.close()
        db = Room.databaseBuilder(context, WalletDatabase::class.java, name).build()
        try {
            assertEquals(1, db.dao().observeTransactions(entry.scope).first().size)
            assertEquals(-1234L, db.dao().observeTransactions(entry.scope).first().first().amountCents)
            assertEquals(8766L, db.dao().observeBalance(entry.scope).first()!!.cents)
            assertEquals("op2", db.dao().pending(entry.scope)!!.operationId)
            assertTrue(db.dao().observeTransactions("another-server|CTA001").first().isEmpty())
        } finally { db.close() }
    }
}
