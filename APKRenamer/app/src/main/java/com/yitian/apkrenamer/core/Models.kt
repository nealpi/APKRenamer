package com.yitian.apkrenamer.core

/**
 * 改名进度上报回调。所有进度都会跨线程切换到主线程更新 UI。
 */
fun interface ProgressListener {
    fun onProgress(stage: String, percent: Int)
}

/**
 * 一次改名任务的结果。
 */
sealed class RenameResult {
    data class Success(
        val outputPath: String,
        val newPackageName: String,
        val newDisplayName: String,
    ) : RenameResult()

    data class Failure(
        val stage: String,
        val message: String,
        val cause: Throwable? = null,
    ) : RenameResult()
}
