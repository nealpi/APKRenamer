package com.yitian.apkrenamer.core

import android.content.Context
import android.util.Log
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import java.io.File
import java.math.BigInteger
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.PrivateKey
import java.security.SecureRandom
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class KeystoreManager(private val context: Context) {

    companion object {
        private const val TAG = "KeystoreManager"
        private const val KEYSTORE_FILENAME = "apkrenamer.jks"
        private const val KEY_ALIAS = "apkrenamer"
        private const val STORE_PASSWORD = "apkrenamer"
        private const val KEY_PASSWORD = "apkrenamer"
        private const val KEY_SIZE = 2048
        private const val VALIDITY_DAYS = 25 * 365L
        private const val SIG_ALGORITHM = "SHA256withRSA"

        init {
            // 关键修复：Android 内置的 "BC" 是阉割版（缺签名算法），
            // 必须先把它卸了，再把我们打包的完整 BouncyCastle 插到最前面。
            Security.removeProvider(BouncyCastleProvider.PROVIDER_NAME)
            Security.insertProviderAt(BouncyCastleProvider(), 1)
        }
    }

    private val keystoreFile: File
        get() = File(context.filesDir, KEYSTORE_FILENAME)

    @Synchronized
    fun loadOrCreate(): SigningMaterial {
        val ks = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            try {
                keystoreFile.inputStream().use { ks.load(it, STORE_PASSWORD.toCharArray()) }
                val key = ks.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
                val cert = ks.getCertificate(KEY_ALIAS) as X509Certificate
                Log.d(TAG, "loaded existing keystore")
                return SigningMaterial(key, cert)
            } catch (t: Throwable) {
                Log.w(TAG, "existing keystore corrupted, regenerating", t)
                keystoreFile.delete()
            }
        }
        return createAndPersist(ks)
    }

    private fun createAndPersist(ks: KeyStore): SigningMaterial {
        Log.d(TAG, "generating new keystore")

        val keyPair = KeyPairGenerator.getInstance("RSA").run {
            initialize(KEY_SIZE, SecureRandom())
            generateKeyPair()
        }

        val now = Date()
        val notAfter = Date(now.time + VALIDITY_DAYS * 24L * 60L * 60L * 1000L)
        val subject = X500Name("CN=APKRenamer, OU=Local, O=Local, L=Local, ST=Local, C=ZZ")
        val serial = BigInteger(64, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            subject, serial, now, notAfter, subject, keyPair.public
        )
        // 显式指定 provider，确保用我们打包的完整版 BouncyCastle
        val signer = JcaContentSignerBuilder(SIG_ALGORITHM)
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .build(keyPair.private)
        val certHolder = certBuilder.build(signer)
        val cert: X509Certificate = JcaX509CertificateConverter()
            .setProvider(BouncyCastleProvider.PROVIDER_NAME)
            .getCertificate(certHolder)

        ks.load(null, null)
        ks.setKeyEntry(
            KEY_ALIAS,
            keyPair.private,
            KEY_PASSWORD.toCharArray(),
            arrayOf(cert),
        )
        keystoreFile.outputStream().use { ks.store(it, STORE_PASSWORD.toCharArray()) }

        return SigningMaterial(keyPair.private, cert)
    }

    data class SigningMaterial(
        val privateKey: PrivateKey,
        val certificate: X509Certificate,
    )
}
