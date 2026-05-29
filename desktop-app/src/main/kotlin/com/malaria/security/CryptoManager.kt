package com.malaria.security

import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Шифрование строковых полей БД (AES-256/GCM).
 *
 * Зашифрованные значения помечаются префиксом "enc:v1:" — это позволяет
 * читать старые незашифрованные записи без миграции (decrypt вернёт их как есть).
 *
 * ВНИМАНИЕ: passphrase зашит в коде. Для учебного проекта это приемлемо,
 * для прод-системы ключ должен браться из OS keystore или вводиться пользователем.
 */
object CryptoManager {
    private const val PREFIX = "enc:v1:"
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val IV_LENGTH = 12       // 96 бит — рекомендация NIST для GCM
    private const val TAG_LENGTH = 128     // бит
    private const val KEY_LENGTH = 256     // бит
    private const val ITERATIONS = 65_536

    private const val PASSPHRASE = "malaria-detection-secret-v1"
    private val SALT = byteArrayOf(
        0x4D, 0x61, 0x6C, 0x61, 0x72, 0x69, 0x61, 0x21,
        0x53, 0x61, 0x6C, 0x74, 0x32, 0x30, 0x32, 0x36
    )

    private val secretKey: SecretKey by lazy { deriveKey() }
    private val secureRandom = SecureRandom()

    private fun deriveKey(): SecretKey {
        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(PASSPHRASE.toCharArray(), SALT, ITERATIONS, KEY_LENGTH)
        val keyBytes = factory.generateSecret(spec).encoded
        return SecretKeySpec(keyBytes, "AES")
    }

    /** Шифрует строку: "enc:v1:" + base64(IV || ciphertext+tag). */
    fun encrypt(plaintext: String): String {
        val iv = ByteArray(IV_LENGTH).also { secureRandom.nextBytes(it) }
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))
        return PREFIX + Base64.getEncoder().encodeToString(iv + ciphertext)
    }

    /**
     * Расшифровывает строку.
     * Если префикса нет — возвращает значение как есть (обратная совместимость
     * со старыми незашифрованными записями).
     */
    fun decrypt(value: String): String {
        if (!value.startsWith(PREFIX)) return value
        return try {
            val combined = Base64.getDecoder().decode(value.removePrefix(PREFIX))
            val iv = combined.copyOfRange(0, IV_LENGTH)
            val ciphertext = combined.copyOfRange(IV_LENGTH, combined.size)
            val cipher = Cipher.getInstance(ALGORITHM)
            cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH, iv))
            String(cipher.doFinal(ciphertext), Charsets.UTF_8)
        } catch (e: Exception) {
            println("⚠️ Не удалось расшифровать значение: ${e.message}")
            value
        }
    }
}
