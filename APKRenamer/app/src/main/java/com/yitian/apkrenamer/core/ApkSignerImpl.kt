package com.yitian.apkrenamer.core

import android.util.Log
import com.android.apksig.ApkSigner
import com.android.apksig.ApkSigner.SignerConfig
import java.io.File

/**
 * 用 Google 官方 apksig 库给 APK 加 v1+v2+v3 签名。
 *
 * 输入：unsigned.apk
 * 输出：signed.apk（同目录）
 *
 * apksig 内部会顺带做 zipalign（v2 签名必须对齐到 4 字节）。
 */
object ApkSignerImpl {

    private const val TAG = "ApkSignerImpl"

    fun sign(
        unsignedApk: File,
        signedApk: File,
        signingMaterial: KeystoreManager.SigningMaterial,
        minSdk: Int = 21,
    ) {
        require(unsignedApk.exists()) { "未签名 APK 不存在: ${unsignedApk.absolutePath}" }
        if (signedApk.exists()) signedApk.delete()

        val signerConfig = SignerConfig.Builder(
            "apkrenamer",
            signingMaterial.privateKey,
            listOf(signingMaterial.certificate),
        ).build()

        val signer = ApkSigner.Builder(listOf(signerConfig))
            .setInputApk(unsignedApk)
            .setOutputApk(signedApk)
            .setMinSdkVersion(minSdk)
            .setV1SigningEnabled(true)
            .setV2SigningEnabled(true)
            .setV3SigningEnabled(true)
            // v4 需要单独 .idsig 旁路文件，Android 11+ 才支持。
            // 对"两个版本并存"这个目标没好处，禁用更省事。
            .setV4SigningEnabled(false)
            .build()

        signer.sign()
        Log.d(TAG, "signed apk size = ${signedApk.length()}")
    }
}
