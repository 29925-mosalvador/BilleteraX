package ec.edu.espe.billeterax

import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import ec.edu.espe.billeterax.data.*
import ec.edu.espe.billeterax.model.*
import ec.edu.espe.billeterax.network.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.math.BigDecimal
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class RegisteredUserFlowTest {
    @Test fun registrationLoginAndTransferWorkWithSeparateAccounts() = runBlocking(Dispatchers.IO) {
        val args = InstrumentationRegistry.getArguments()
        val url = (args.getString("serverUrl") ?: "http://10.0.2.2:8085/").trimEnd('/') + "/"
        val suffix = UUID.randomUUID().toString().take(8)
        val public = WalletNetwork(Config(url))
        val password = "Test-only-" + UUID.randomUUID().toString()
        val a = public.api.register(RegistrationRequest("Ana Prueba", "ana_" + suffix, password))
        val b = public.api.register(RegistrationRequest("Bruno Prueba", "bruno_" + suffix, password))
        val login = public.api.login(LoginRequest(a.user.username.uppercase(), password))
        assertEquals(a.user.accountId, login.user.accountId)
        assertNotEquals(a.user.accountId, b.user.accountId)
        val context = ApplicationProvider.getApplicationContext<Context>()
        val name = "registered-flow-test.db"
        context.deleteDatabase(name)
        val db = Room.databaseBuilder(context, WalletDatabase::class.java, name).build()
        val config = Config(url, a.user.accountId, login.token)
        val repo = WalletRepository(db, config)
        val sender = WalletNetwork(Config(url, b.user.accountId, b.token))
        val session = launch { repo.runSession() }
        try {
            withTimeout(20_000) { repo.connection.first { it == Connection.CONNECTED } }
            repo.synchronize()
            assertEquals(0L, repo.balance.first()!!.cents)
            repo.execute(Operation.DEPOSIT, "5.00", null, null, null)
            sender.api.execute("transactions/deposit", MoneyRequest(UUID.randomUUID().toString(), b.user.accountId, BigDecimal("1.00")))
            val incoming = sender.api.execute("transactions/transfer", MoneyRequest(UUID.randomUUID().toString(), b.user.accountId, BigDecimal("1.00"), a.user.accountId))
            withTimeout(15_000) {
                repo.transactions.first { rows -> rows.any { it.operationId == incoming.operationId && it.type == "TRANSFER_IN" } }
                repo.balance.first { it?.cents == 600L }
            }
            assertNull(repo.pending.first())
            assertEquals(a.user.username, WalletNetwork(config).let { network -> try { network.api.me().username } finally { network.close() } })
            repo.synchronize()
            assertEquals(2, repo.transactions.first().size)
        } finally {
            session.cancelAndJoin(); repo.close(); sender.close(); public.close(); db.close(); context.deleteDatabase(name)
        }
    }
    @Test fun encryptedSessionAndThemeSurviveReopeningAndLogoutClearsOnlyCredentials() {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val prefName = "test_auth_" + UUID.randomUUID().toString()
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int): SharedPreferences = base.getSharedPreferences(prefName, mode)
        }
        try {
            val token = UUID.randomUUID().toString() + UUID.randomUUID().toString()
            val config = Config("http://192.168.18.76:8080/", "CTA1234567890", token)
            val profile = UserProfile("test-user", "ana", "Ana Prueba", config.account)
            ConfigStore(isolated).apply { save(config, profile); setAppearance(Appearance.DARK) }
            val reopened = ConfigStore(isolated)
            assertEquals(config, reopened.load()); assertEquals(profile, reopened.profile())
            assertEquals(Appearance.DARK, reopened.appearance())
            val raw = base.getSharedPreferences(prefName, Context.MODE_PRIVATE).getString("token", "")!!
            assertFalse(raw.contains(token))
            reopened.clearSession()
            assertTrue(reopened.load().token.isEmpty()); assertNull(reopened.profile())
            assertEquals(Appearance.DARK, reopened.appearance()); assertEquals(config.url, reopened.load().url)
        } finally { base.deleteSharedPreferences(prefName) }
    }
}
