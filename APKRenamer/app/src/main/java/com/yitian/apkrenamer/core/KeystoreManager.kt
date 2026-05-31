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

/**
 * 管理一个长期复用的自签 keystore。
 *
 * 设计原则：
 *  - 第一次跑时生成一个 RSA-2048 的 25 年期自签证书，存到 filesDir/apkrenamer.jks
 *  - 之后所有改名出来的 APK 都用同一把 key 签名
 *  - 这样用户用本工具改出的多个 APK 之间可以互相覆盖更新（同 key 同包名时）
 *  - keystore 密码写死在代码里（用户视角无感知；不是用来抗逆向的，只为 JCA API 满意）
 */
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
            // BouncyCastle Provider 注册：放静态块，多次调用 idempotent
            if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    private val keystoreFile: File
        get() = File(context.filesDir, KEYSTORE_FILENAME)

    /**
     * 返回签名所需的 (PrivateKey, X509Certificate) 对。
     * 若 keystore 不存在则首次生成。线程安全：synchronized 防并发首次生成。
     */
    @Synchronized
    fun loadOrCreate(): SigningMaterial {
        val ks = KeyStore.getInstance("PKCS12")
        if (keystoreFile.exists()) {
            try {
                keystoreFile.inputStream().use { ks.load(it, STORE_PASSWORD.toCharArray()) }
                val key = ks.getKey(KEY_ALIAS, KEY_PASSWORD.toCharArray()) as PrivateKey
                val cert = ks.getCertificate(KEY_ALIAS) as X509Certificate
                Log.d(TAG, "loaded existing keystore (cert subject = ${cert.subjectX500Principal})")
                return SigningMaterial(key, cert)
            } catch (t: Throwable) {
                // 文件存在但解析失败 —— 损坏了，丢掉重生成
                Log.w(TAG, "existing keystore corrupted, regenerating", t)
                keystoreFile.delete()
            }
        }
        return createAndPersist(ks)
    }

    private fun createAndPersist(ks: KeyStore): SigningMaterial {
        Log.d(TAG, "generating new keystore at ${keystoreFile.absolutePath}")

        val keyPair = KeyPairGenerator.getInstance("RSA").run {
            initialize(KEY_SIZE, SecureRandom())
            generateKeyPair()
        }

        val now = Date()
        val notAfter = Date(now.time + VALIDITY_DAYS * 24L * 60L * 60L * 1000L)
        val subject = X500Name("CN=APKRenamer, OU=Local, O=Local, L=Local, ST=Local, C=ZZ")
        val serial = BigInteger(64, SecureRandom())

        val certBuilder = JcaX509v3CertificateBuilder(
            /* issuer    = */ subject,   // 自签
            /* serial    = */ serial,
            /* notBefore = */ now,
            /* notAfter  = */ notAfter,
            /* subject   = */ subject,
            /* publicKey = */ keyPair.public,
        )
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
