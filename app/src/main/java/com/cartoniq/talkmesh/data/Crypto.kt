package com.cartoniq.talkmesh.data

import android.util.Base64
import java.security.KeyFactory
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import javax.crypto.Cipher
import javax.crypto.KeyAgreement
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/** Encryption helpers: ECDH (P-256) for direct messages, PBKDF2 room keys, AES-256-GCM for payloads. */
object Crypto {
    private val rng = SecureRandom()
    private const val ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun newKeyPair(): KeyPair {
        val g = KeyPairGenerator.getInstance("EC")
        g.initialize(ECGenParameterSpec("secp256r1"))
        return g.generateKeyPair()
    }

    fun pubB64(k: KeyPair): String = Base64.encodeToString(k.public.encoded, Base64.NO_WRAP)
    fun privB64(k: KeyPair): String = Base64.encodeToString(k.private.encoded, Base64.NO_WRAP)

    fun restore(pub: String, priv: String): KeyPair {
        val kf = KeyFactory.getInstance("EC")
        return KeyPair(
            kf.generatePublic(X509EncodedKeySpec(Base64.decode(pub, Base64.NO_WRAP))),
            kf.generatePrivate(PKCS8EncodedKeySpec(Base64.decode(priv, Base64.NO_WRAP)))
        )
    }

    /** Shared AES key between me and a peer, from their public key. */
    fun shared(mine: KeyPair, theirPubB64: String): SecretKeySpec {
        val their = KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(Base64.decode(theirPubB64, Base64.NO_WRAP)))
        val ka = KeyAgreement.getInstance("ECDH")
        ka.init(mine.private)
        ka.doPhase(their, true)
        return SecretKeySpec(MessageDigest.getInstance("SHA-256").digest(ka.generateSecret()), "AES")
    }

    fun encrypt(key: SecretKeySpec, plain: String): String {
        val iv = ByteArray(12).also { rng.nextBytes(it) }
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(128, iv))
        return Base64.encodeToString(iv + c.doFinal(plain.toByteArray(Charsets.UTF_8)), Base64.NO_WRAP)
    }

    fun decrypt(key: SecretKeySpec, b64: String): String? = try {
        val all = Base64.decode(b64, Base64.NO_WRAP)
        val c = Cipher.getInstance("AES/GCM/NoPadding")
        c.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, all.copyOfRange(0, 12)))
        String(c.doFinal(all.copyOfRange(12, all.size)), Charsets.UTF_8)
    } catch (e: Exception) { null }

    /** 10 random characters (about 50 bits), easy to read out loud. */
    fun newCode(): String = (1..10).map { ALPHABET[rng.nextInt(ALPHABET.length)] }.joinToString("")

    fun roomKey(code: String): SecretKeySpec {
        val spec = PBEKeySpec(code.trim().uppercase().toCharArray(), "talkmesh-room-v1".toByteArray(), 20_000, 256)
        return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
    }

    /** Public room id derived from the (slow) key, so it does not leak the code cheaply. */
    fun roomId(key: SecretKeySpec): String =
        MessageDigest.getInstance("SHA-256").digest(key.encoded + "id".toByteArray()).take(6).joinToString("") { "%02x".format(it) }
}
