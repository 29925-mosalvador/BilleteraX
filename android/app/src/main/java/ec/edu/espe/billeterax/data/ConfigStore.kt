package ec.edu.espe.billeterax.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import ec.edu.espe.billeterax.model.*

/** Passwords are never stored. Session tokens use Android Keystore encryption. Call on IO. */
class ConfigStore(context: Context) {
    private val prefs = context.getSharedPreferences("connection", Context.MODE_PRIVATE)
    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey("billeterax-token", null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder("billeterax-token", KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    fun load(): Config {
        val token = prefs.getString("token", null)?.let { encrypted ->
            runCatching {
                val parts = encrypted.split(":")
                val cipher = Cipher.getInstance("AES/GCM/NoPadding")
                cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, Base64.decode(parts[0], Base64.NO_WRAP)))
                String(cipher.doFinal(Base64.decode(parts[1], Base64.NO_WRAP)), Charsets.UTF_8)
            }.getOrDefault("")
        } ?: ""
        return Config(prefs.getString("url", Config().url)!!, prefs.getString("account", "")!!, token)
    }
    fun profile(): UserProfile? {
        val id = prefs.getString("userId", null) ?: return null
        return UserProfile(id, prefs.getString("username", "")!!, prefs.getString("displayName", "")!!, prefs.getString("account", "")!!)
    }
    fun appearance(): Appearance = runCatching { Appearance.valueOf(prefs.getString("appearance", "SYSTEM")!!) }.getOrDefault(Appearance.SYSTEM)
    fun setAppearance(value: Appearance) { check(prefs.edit().putString("appearance", value.name).commit()) }
    fun saveServer(url: String) { check(prefs.edit().putString("url", url).commit()) }
    fun save(config: Config, profile: UserProfile? = null) {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        val encrypted = cipher.doFinal(config.token.toByteArray(Charsets.UTF_8))
        val token = Base64.encodeToString(cipher.iv, Base64.NO_WRAP) + ":" + Base64.encodeToString(encrypted, Base64.NO_WRAP)
        val editor = prefs.edit().putString("url", config.url).putString("account", config.account).putString("token", token)
        if (profile != null) editor.putString("userId", profile.id).putString("username", profile.username).putString("displayName", profile.displayName)
        else editor.remove("userId").remove("username").remove("displayName")
        check(editor.commit())
    }
    fun clearSession() {
        check(prefs.edit().remove("token").remove("account").remove("userId").remove("username").remove("displayName").commit())
    }
}
