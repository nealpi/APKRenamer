package com.yitian.apkrenamer.ui

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yitian.apkrenamer.core.ApkRenamer
import com.yitian.apkrenamer.core.ApkSignerImpl
import com.yitian.apkrenamer.core.KeystoreManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RenameViewModel(app: Application) : AndroidViewModel(app) {

    sealed class UiState {
        data object Idle : UiState()
        data class Running(val stage: String, val percent: Int) : UiState()
        data class NeedSaveLocation(
            val signedApk: java.io.File,
            val newPackageName: String,
            val displayName: String,
            val suggestedFilename: String,
            val cleanup: () -> Unit,
        ) : UiState()
        data class Done(val savedDisplayName: String, val packageName: String) : UiState()
        data class Failed(val stage: String, val message: String) : UiState()
    }

    private val _state = MutableStateFlow<UiState>(UiState.Idle)
    val state: StateFlow<UiState> = _state.asStateFlow()

    // 关键修复：标记保存对话框是否已经弹过，防止 lifecycle 重启时重弹
    private var saveLauncherConsumed = false

    private val _selectedApkUri = MutableStateFlow<Uri?>(null)
    val selectedApkUri: StateFlow<Uri?> = _selectedApkUri.asStateFlow()

    private val _selectedApkName = MutableStateFlow<String?>(null)
    val selectedApkName: StateFlow<String?> = _selectedApkName.asStateFlow()

    fun setSelectedApk(uri: Uri?, displayName: String?) {
        _selectedApkUri.value = uri
        _selectedApkName.value = displayName
        if (_state.value !is UiState.Running) {
            _state.value = UiState.Idle
        }
    }

    fun startRename(displayName: String) {
        val uri = _selectedApkUri.value
        if (uri == null) {
            _state.value = UiState.Failed("准备", "请先选择 APK 文件")
            return
        }
        if (displayName.isBlank()) {
            _state.value = UiState.Failed("准备", "请输入新的应用显示名")
            return
        }
        viewModelScope.launch {
            runRename(uri, displayName.trim())
        }
    }

    fun reset() {
        _state.value = UiState.Idle
        saveLauncherConsumed = false
    }

    /**
     * 给 UI 调用：取出待保存的请求；只会成功取出一次。
     * 之后即使 lifecycle 重启 collect 把同样 state 再发一遍，这里会返回 null。
     */
    fun consumeSaveLocationRequest(): UiState.NeedSaveLocation? {
        val s = _state.value as? UiState.NeedSaveLocation ?: return null
        if (saveLauncherConsumed) return null
        saveLauncherConsumed = true
        return s
    }

    private suspend fun runRename(inputUri: Uri, displayName: String) {
        val ctx: Context = getApplication()
        saveLauncherConsumed = false
        _state.value = UiState.Running("准备…", 1)

        val outcome = try {
            withContext(Dispatchers.IO) {
                ApkRenamer(ctx).rename(inputUri, displayName) { stage, percent ->
                    _state.value = UiState.Running(stage, percent)
                }
            }
        } catch (t: Throwable) {
            _state.value = UiState.Failed("改包名", t.message ?: t.javaClass.simpleName)
            return
        }

        val signed = try {
            withContext(Dispatchers.IO) {
                _state.value = UiState.Running("正在生成签名…", 82)
                val material = KeystoreManager(ctx).loadOrCreate()
                _state.value = UiState.Running("正在签名…", 90)
                val signedFile = java.io.File(outcome.workDir, "signed.apk")
                ApkSignerImpl.sign(outcome.unsignedApk, signedFile, material)
                signedFile
            }
        } catch (t: Throwable) {
            outcome.cleanup()
            _state.value = UiState.Failed("签名", t.message ?: t.javaClass.simpleName)
            return
        }

        _state.value = UiState.NeedSaveLocation(
            signedApk = signed,
            newPackageName = outcome.newPackageName,
            displayName = displayName,
            suggestedFilename = sanitizeFilename(displayName) + ".apk",
            cleanup = { outcome.cleanup() },
        )
    }

    fun saveToUserUri(outputUri: Uri) {
        val s = _state.value as? UiState.NeedSaveLocation ?: return
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    getApplication<Application>().contentResolver
                        .openOutputStream(outputUri, "w")
                        .use { out ->
                            requireNotNull(out) { "无法写入选择的位置" }
                            s.signedApk.inputStream().use { it.copyTo(out) }
                        }
                }
                s.cleanup()
                _state.value = UiState.Done(s.displayName, s.newPackageName)
            } catch (t: Throwable) {
                _state.value = UiState.Failed("保存", t.message ?: t.javaClass.simpleName)
            }
        }
    }

    fun cancelSave() {
        (_state.value as? UiState.NeedSaveLocation)?.cleanup?.invoke()
        _state.value = UiState.Idle
        saveLauncherConsumed = false
    }

    private fun sanitizeFilename(input: String): String {
        val filtered = input.map { c ->
            if (c.isLetterOrDigit() || c == '-' || c == '_' || c in '一'..'鿿') c else '_'
        }.joinToString("")
        return filtered.ifBlank { "renamed" }
    }
}
