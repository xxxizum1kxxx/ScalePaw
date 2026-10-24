package com.izum1k.scalepaw.data.googlehealth

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

data class GoogleHealthState(
    val clientId: String = "",
    val hasSecret: Boolean = false,
    val connected: Boolean = false,
    val account: String? = null,
    val linkedUserId: Long? = null,
    val lastSync: Long = 0L,
    val lastError: String? = null,
    val needsReauth: Boolean = false,
) {
    val hasCredentials: Boolean get() = clientId.isNotBlank() && hasSecret
}

@Serializable
data class LedgerEntry(val dataType: String, val hash: String)

/** What has already been pushed, keyed by data point id. */
interface HealthLedger {
    fun load(): Map<String, LedgerEntry>
    fun save(entries: Map<String, LedgerEntry>)
}

/**
 * Credentials, tokens and sync status. The file is excluded from backups because the secret and the
 * refresh token are sealed with a Keystore key that never leaves the device.
 */
class GoogleHealthStore(context: Context) : HealthLedger {
    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val ledgerFile = File(context.noBackupFilesDir, "google_health_ledger.json")
    private val _state = MutableStateFlow(read())
    val state: StateFlow<GoogleHealthState> = _state.asStateFlow()

    private fun read() = GoogleHealthState(
        clientId = prefs.getString(K_CLIENT_ID, null).orEmpty(),
        hasSecret = prefs.contains(K_SECRET),
        connected = prefs.contains(K_REFRESH),
        account = prefs.getString(K_ACCOUNT, null),
        linkedUserId = prefs.getLong(K_LINKED_USER, 0L).takeIf { it > 0L },
        lastSync = prefs.getLong(K_LAST_SYNC, 0L),
        lastError = prefs.getString(K_LAST_ERROR, null),
        needsReauth = prefs.getBoolean(K_REAUTH, false),
    )

    private fun update(block: android.content.SharedPreferences.Editor.() -> Unit) {
        prefs.edit(commit = true, action = block)
        _state.value = read()
    }

    fun clientSecret(): String? = prefs.getString(K_SECRET, null)?.let(Sealer::open)

    fun refreshToken(): String? = prefs.getString(K_REFRESH, null)?.let(Sealer::open)

    fun setCredentials(clientId: String, clientSecret: String?) = update {
        putString(K_CLIENT_ID, clientId.trim())
        if (!clientSecret.isNullOrBlank()) putString(K_SECRET, Sealer.seal(clientSecret.trim()))
    }

    fun connect(refreshToken: String, account: String?, linkedUserId: Long) {
        if (account == null || account != prefs.getString(K_ACCOUNT, null)) save(emptyMap())
        update {
            putString(K_REFRESH, Sealer.seal(refreshToken))
            if (account != null) putString(K_ACCOUNT, account) else remove(K_ACCOUNT)
            putLong(K_LINKED_USER, linkedUserId)
            putLong(K_LAST_SYNC, 0L)
            remove(K_LAST_ERROR)
            putBoolean(K_REAUTH, false)
        }
    }

    fun disconnect() {
        save(emptyMap())
        update {
            remove(K_REFRESH)
            remove(K_ACCOUNT)
            remove(K_LAST_ERROR)
            putLong(K_LAST_SYNC, 0L)
            putBoolean(K_REAUTH, false)
        }
    }

    fun setLinkedUser(id: Long) = update { putLong(K_LINKED_USER, id) }

    fun recordSuccess(at: Long) = update {
        putLong(K_LAST_SYNC, at)
        remove(K_LAST_ERROR)
    }

    fun recordError(message: String, needsReauth: Boolean = false) = update {
        putString(K_LAST_ERROR, message)
        if (needsReauth) putBoolean(K_REAUTH, true)
    }

    override fun load(): Map<String, LedgerEntry> =
        runCatching { json.decodeFromString<Map<String, LedgerEntry>>(ledgerFile.readText()) }.getOrDefault(emptyMap())

    override fun save(entries: Map<String, LedgerEntry>) {
        val tmp = File(ledgerFile.parentFile, ledgerFile.name + ".tmp")
        tmp.writeText(json.encodeToString(entries))
        tmp.renameTo(ledgerFile)
    }

    /** AES-GCM with a non-exportable Keystore key; output is base64(iv + ciphertext). */
    private object Sealer {
        private const val ALIAS = "scalepaw_google_health"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val IV_BYTES = 12

        private fun key(): SecretKey {
            val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
            val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore")
            generator.init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build(),
            )
            return generator.generateKey()
        }

        fun seal(plain: String): String {
            val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, key()) }
            return Base64.encodeToString(cipher.iv + cipher.doFinal(plain.toByteArray()), Base64.NO_WRAP)
        }

        fun open(sealed: String): String? = runCatching {
            val bytes = Base64.decode(sealed, Base64.NO_WRAP)
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, bytes, 0, IV_BYTES))
            String(cipher.doFinal(bytes, IV_BYTES, bytes.size - IV_BYTES))
        }.getOrNull()
    }

    private companion object {
        const val PREFS = "google_health"
        const val K_CLIENT_ID = "client_id"
        const val K_SECRET = "client_secret"
        const val K_REFRESH = "refresh_token"
        const val K_ACCOUNT = "account"
        const val K_LINKED_USER = "linked_user"
        const val K_LAST_SYNC = "last_sync"
        const val K_LAST_ERROR = "last_error"
        const val K_REAUTH = "needs_reauth"
        val json = Json { ignoreUnknownKeys = true }
    }
}
