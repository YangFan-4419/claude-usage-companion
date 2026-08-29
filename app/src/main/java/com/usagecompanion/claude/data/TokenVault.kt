package com.usagecompanion.claude.data

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** 一份凭证：访问令牌 + 用于续期的刷新令牌 + 过期时刻（epoch 毫秒，0 表示未知）。 */
data class TokenBundle(
    val accessToken: String,
    val refreshToken: String?,
    val expiresAt: Long,
)

class TokenVault(context: Context) {
    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("token_vault", Context.MODE_PRIVATE)

    /**
     * 接受三种输入：
     *  1. Claude Code 的 .credentials.json 全文（带 claudeAiOauth 外层）
     *  2. 取 token 脚本生成的精简 JSON
     *  3. 光秃秃的一个 access token（此时没有刷新令牌，退化成旧行为）
     */
    fun saveToken(raw: String) {
        if (raw.isBlank()) {
            prefs.edit().clear().apply()
            return
        }
        writeBundle(parseInput(raw.trim()))
    }

    /** 续期成功后写回新令牌；服务端没下发新刷新令牌时沿用旧的。 */
    fun updateTokens(accessToken: String, refreshToken: String?, expiresAt: Long) {
        val previous = readBundle()
        writeBundle(
            TokenBundle(
                accessToken = accessToken,
                refreshToken = refreshToken ?: previous?.refreshToken,
                expiresAt = expiresAt,
            )
        )
    }

    fun readToken(): String? = readBundle()?.accessToken?.takeIf { it.isNotBlank() }

    fun readRefreshToken(): String? = readBundle()?.refreshToken?.takeIf { it.isNotBlank() }

    fun expiresAt(): Long = readBundle()?.expiresAt ?: 0L

    fun hasRefreshToken(): Boolean = !readRefreshToken().isNullOrBlank()

    private fun parseInput(raw: String): TokenBundle {
        if (!raw.startsWith("{")) return TokenBundle(raw, null, 0L)
        return runCatching {
            val root = JSONObject(raw)
            val o = root.optJSONObject("claudeAiOauth") ?: root
            val access = firstNonBlank(o.optString("accessToken"), o.optString("access_token"))
            val refresh = firstNonBlank(o.optString("refreshToken"), o.optString("refresh_token"))
            val expires = normalizeEpochMillis(
                if (o.has("expiresAt")) o.optLong("expiresAt", 0L) else o.optLong("expires_at", 0L)
            )
            if (access.isBlank()) error("no access token in json")
            TokenBundle(access, refresh.ifBlank { null }, expires)
        }.getOrElse { TokenBundle(raw, null, 0L) }
    }

    private fun firstNonBlank(vararg values: String): String =
        values.firstOrNull { it.isNotBlank() && it != "null" }.orEmpty()

    /** 有的来源给秒，有的给毫秒，统一成毫秒。 */
    private fun normalizeEpochMillis(value: Long): Long = when {
        value <= 0L -> 0L
        value < 100_000_000_000L -> value * 1000L
        else -> value
    }

    private fun writeBundle(bundle: TokenBundle) {
        val payload = JSONObject().apply {
            put("accessToken", bundle.accessToken)
            put("refreshToken", bundle.refreshToken ?: "")
            put("expiresAt", bundle.expiresAt)
        }.toString()

        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getOrCreateKey())
        val encrypted = cipher.doFinal(payload.toByteArray(Charsets.UTF_8))

        prefs.edit()
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .putString(KEY_BUNDLE, Base64.encodeToString(encrypted, Base64.NO_WRAP))
            .remove(KEY_LEGACY_TOKEN)
            .apply()
    }

    private fun readBundle(): TokenBundle? {
        val iv = prefs.getString(KEY_IV, null) ?: return null
        val encrypted = prefs.getString(KEY_BUNDLE, null)
            ?: prefs.getString(KEY_LEGACY_TOKEN, null)
            ?: return null

        return runCatching {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(
                Cipher.DECRYPT_MODE,
                getOrCreateKey(),
                GCMParameterSpec(128, Base64.decode(iv, Base64.NO_WRAP)),
            )
            val plain = String(cipher.doFinal(Base64.decode(encrypted, Base64.NO_WRAP)), Charsets.UTF_8)
            if (plain.startsWith("{")) {
                val o = JSONObject(plain)
                TokenBundle(
                    accessToken = o.optString("accessToken"),
                    refreshToken = o.optString("refreshToken").ifBlank { null },
                    expiresAt = o.optLong("expiresAt", 0L),
                )
            } else {
                // 旧版本存的是裸 token，直接兼容
                TokenBundle(plain, null, 0L)
            }
        }.getOrNull()
    }

    private fun getOrCreateKey(): SecretKey {
        val keyStore = KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let {
            return it.secretKey
        }

        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setRandomizedEncryptionRequired(true)
            .build()
        keyGenerator.init(spec)
        return keyGenerator.generateKey()
    }

    private companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "claude_usage_companion_token"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val KEY_IV = "iv"
        const val KEY_BUNDLE = "bundle"
        const val KEY_LEGACY_TOKEN = "token"
    }
}
