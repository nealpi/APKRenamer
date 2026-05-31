package com.yitian.apkrenamer.core

import java.security.MessageDigest

/**
 * 把用户输入的【应用显示名】（如 "微信备份"）确定性地映射为合法的包名后缀。
 *
 * 规则：
 *  - 取 UTF-8 SHA-1 前 6 字节 → 12 位十六进制
 *  - 拼成 ".clone_xxxxxxxxxxxx" 追加到原包名
 *
 * 这么做的好处：
 *  - 同一个显示名永远生成同一个包名 → 用户对同一 APK 反复改名得到相同后缀，
 *    不会因为换台手机或重装而出现"装不上 / 数据丢失"。
 *  - 后缀只有 ASCII，绝不会触发包名格式校验。
 *  - 显示名可以是任意 Unicode，不影响包名合法性。
 */
object PackageNameDeriver {

    private const val SUFFIX_PREFIX = ".clone_"

    fun derive(originalPackage: String, displayName: String): String {
        require(originalPackage.isNotBlank()) { "原包名为空" }
        require(displayName.isNotBlank()) { "显示名为空" }

        val digest = MessageDigest.getInstance("SHA-1")
            .digest(displayName.trim().toByteArray(Charsets.UTF_8))

        val hex = buildString(12) {
            for (i in 0 until 6) {
                val b = digest[i].toInt() and 0xFF
                append(HEX[b ushr 4])
                append(HEX[b and 0x0F])
            }
        }
        return originalPackage + SUFFIX_PREFIX + hex
    }

    private val HEX = "0123456789abcdef".toCharArray()
}
