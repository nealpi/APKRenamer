package com.yitian.apkrenamer.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.reandroid.apk.ApkModule
import com.reandroid.apk.AndroidFrameworks
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import com.reandroid.arsc.chunk.xml.ResXmlAttribute
import com.reandroid.arsc.chunk.xml.ResXmlElement
import java.io.File

/**
 * 核心改名引擎。
 *
 * 整体流程：
 *  1) 把输入 APK 从 SAF Uri 拷到 cacheDir/in.apk（ARSCLib 需要 File 而不是 InputStream）
 *  2) ApkModule.loadApkFile() 加载
 *  3) 改 AndroidManifest：
 *      - 顶层 package 属性
 *      - application 节点的 android:label
 *      - 把 application / activity / service / receiver / provider 的 android:name
 *        全部展开成绝对类名（防止新 package 重新解析时找不到原始类）
 *      - 改写每个 <provider> 的 android:authorities（追加显示名哈希后缀，避免与原 APK 冲突）
 *  4) 改 resources.arsc 里 PackageBlock 的 name —— ARSCLib 自动同步 R 表
 *  5) writeApk() 写到 cacheDir/out.apk（未签名、未对齐）
 *  6) 交给 ApkSigner 做 zipalign + v1/v2/v3 签名 → cacheDir/signed.apk
 *  7) 把 signed.apk 拷贝到用户用 SAF CreateDocument 指定的输出 Uri
 */
class ApkRenamer(private val context: Context) {

    companion object {
        private const val TAG = "ApkRenamer"
    }

    /**
     * @return 改名后的【新包名】与【中间签名 APK 的本地路径】。
     *         调用方负责把这个本地路径流入 SAF outputUri。
     */
    @Throws(Exception::class)
    fun rename(
        inputUri: Uri,
        displayName: String,
        progress: ProgressListener,
    ): RenameOutcome {
        val workDir = File(context.cacheDir, "apkrenamer_${System.currentTimeMillis()}").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            // ----- 1) 拷贝输入 APK 到工作目录 -----
            progress.onProgress("正在读取 APK…", 5)
            val inputFile = File(workDir, "in.apk")
            context.contentResolver.openInputStream(inputUri).use { input ->
                requireNotNull(input) { "无法打开输入 APK" }
                inputFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "input apk size = ${inputFile.length()}")

            // ----- 2) 加载 APK -----
            progress.onProgress("正在解析 APK 结构…", 15)
            val apkModule = ApkModule.loadApkFile(inputFile).apply {
                // ARSCLib 需要一个 framework table 来解析系统资源引用
                setLoadDefaultFramework(true)
            }

            val manifest: AndroidManifestBlock = apkModule.androidManifestBlock
                ?: error("APK 中找不到 AndroidManifest.xml（可能是非标准 APK 或已损坏）")
            val originalPackage: String = manifest.packageName
                ?: error("AndroidManifest 缺少 package 属性")
            Log.d(TAG, "original package = $originalPackage")

            val newPackage = PackageNameDeriver.derive(originalPackage, displayName)
            val authoritySuffix = newPackage.substringAfterLast(".clone_", "")
            Log.d(TAG, "new package = $newPackage  authority suffix = $authoritySuffix")

            // ----- 3) 改 AndroidManifest -----
            progress.onProgress("正在改写 AndroidManifest…", 30)
            ManifestRewriter.rewrite(
                manifest = manifest,
                oldPackage = originalPackage,
                newPackage = newPackage,
                newDisplayName = displayName,
                authoritySuffix = authoritySuffix,
            )

            // ----- 4) 改 resources.arsc 里的 PackageBlock 名 -----
            // 这一步看起来可有可无，但若 Android 校验 manifest 包名与 arsc 包名一致性，会拒绝安装。
            progress.onProgress("正在更新资源表…", 55)
            val tableBlock: TableBlock? = apkModule.tableBlock
            if (tableBlock != null) {
                // 一般 APK 只有一个 PackageBlock；少数（共享 UID 的）有多个，全部改名
                for (pkg: PackageBlock in tableBlock.listPackages()) {
                    if (pkg.name == originalPackage) {
                        pkg.name = newPackage
                        Log.d(TAG, "renamed arsc package: $originalPackage -> $newPackage")
                    }
                }
                // refresh 让 ARSCLib 把内部索引重建一遍
                tableBlock.refresh()
            }

            // refresh 整个 ApkModule，让 ARSCLib 重写 manifest 二进制
            manifest.refresh()

            // ----- 5) 写未签名 APK -----
            progress.onProgress("正在重新打包…", 70)
            val unsignedApk = File(workDir, "unsigned.apk")
            apkModule.writeApk(unsignedApk)
            apkModule.close()
            Log.d(TAG, "unsigned apk size = ${unsignedApk.length()}")

            return RenameOutcome(
                workDir = workDir,
                unsignedApk = unsignedApk,
                newPackageName = newPackage,
            )
        } catch (t: Throwable) {
            // 失败时清理工作目录
            workDir.deleteRecursively()
            throw t
        }
    }

    /**
     * rename() 的产物。调用方拿到 unsignedApk 后应交给 ApkSigner 签名，
     * 然后拷到用户选择的 SAF 位置，最后调 cleanup() 删工作目录。
     */
    data class RenameOutcome(
        val workDir: File,
        val unsignedApk: File,
        val newPackageName: String,
    ) {
        fun cleanup() {
            workDir.deleteRecursively()
        }
    }
}
