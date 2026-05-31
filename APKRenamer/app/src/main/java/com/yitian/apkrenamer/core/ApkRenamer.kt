package com.yitian.apkrenamer.core

import android.content.Context
import android.net.Uri
import android.util.Log
import com.reandroid.apk.ApkModule
import com.reandroid.arsc.chunk.PackageBlock
import com.reandroid.arsc.chunk.TableBlock
import com.reandroid.arsc.chunk.xml.AndroidManifestBlock
import java.io.File

/**
 * 核心改名引擎（已适配 ARSCLib V1.3.8 / APKEditor V1.4.9 API）。
 */
class ApkRenamer(private val context: Context) {

    companion object {
        private const val TAG = "ApkRenamer"
    }

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
            // ----- 1) 拷贝输入 APK -----
            progress.onProgress("正在读取 APK…", 5)
            val inputFile = File(workDir, "in.apk")
            context.contentResolver.openInputStream(inputUri).use { input ->
                requireNotNull(input) { "无法打开输入 APK" }
                inputFile.outputStream().use { output -> input.copyTo(output) }
            }
            Log.d(TAG, "input apk size = ${inputFile.length()}")

            // ----- 2) 加载 APK -----
            progress.onProgress("正在解析 APK 结构…", 15)
            val apkModule = ApkModule.loadApkFile(inputFile)

            val manifest: AndroidManifestBlock = apkModule.androidManifestBlock
                ?: error("APK 中找不到 AndroidManifest.xml")
            val originalPackage: String = manifest.packageName
                ?: error("AndroidManifest 缺少 package 属性")
            Log.d(TAG, "original package = $originalPackage")

            val newPackage = PackageNameDeriver.derive(originalPackage, displayName)
            val authoritySuffix = newPackage.substringAfterLast(".clone_", "")
            Log.d(TAG, "new package = $newPackage")

            // ----- 3) 改 AndroidManifest -----
            progress.onProgress("正在改写 AndroidManifest…", 30)
            ManifestRewriter.rewrite(
                manifest = manifest,
                oldPackage = originalPackage,
                newPackage = newPackage,
                newDisplayName = displayName,
                authoritySuffix = authoritySuffix,
            )

            // ----- 4) 改 resources.arsc 的 PackageBlock 名 -----
            progress.onProgress("正在更新资源表…", 55)
            val tableBlock: TableBlock? = apkModule.tableBlock
            if (tableBlock != null) {
                for (pkg: PackageBlock in tableBlock.listPackages()) {
                    if (pkg.name == originalPackage) {
                        pkg.name = newPackage
                        Log.d(TAG, "renamed arsc package: $originalPackage -> $newPackage")
                    }
                }
                tableBlock.refresh()
            }

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
            workDir.deleteRecursively()
            throw t
        }
    }

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
