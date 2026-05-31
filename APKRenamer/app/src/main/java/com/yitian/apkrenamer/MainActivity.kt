package com.yitian.apkrenamer

import android.app.AlertDialog
import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.yitian.apkrenamer.databinding.ActivityMainBinding
import com.yitian.apkrenamer.ui.RenameViewModel
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val vm: RenameViewModel by viewModels()

    // 选择输入 APK
    private val pickApkLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            // 持久化 URI 权限，跨进程跨重启都能用
            try {
                contentResolver.takePersistableUriPermission(
                    uri,
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) { /* 部分提供方不允许，忽略 */ }
            val name = queryDisplayName(uri)
            vm.setSelectedApk(uri, name)
        }
    }

    // 选择输出位置
    private val saveApkLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/vnd.android.package-archive")
    ) { uri: Uri? ->
        if (uri == null) {
            vm.cancelSave()
        } else {
            vm.saveToUserUri(uri)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnPickApk.setOnClickListener {
            // mime "application/vnd.android.package-archive" 在不少 ROM 上过滤后没结果，
            // 兼容性最好的做法是用 */*。
            pickApkLauncher.launch(arrayOf("*/*"))
        }

        binding.btnStart.setOnClickListener {
            vm.startRename(binding.editDisplayName.text?.toString().orEmpty())
        }

        binding.btnReset.setOnClickListener {
            vm.reset()
            binding.editDisplayName.setText("")
            vm.setSelectedApk(null, null)
        }

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    vm.selectedApkName.collect { name ->
                        binding.tvSelectedApk.text = name?.let { "已选择：$it" } ?: "尚未选择 APK"
                    }
                }
                launch {
                    vm.state.collect { renderState(it) }
                }
            }
        }
    }

    private fun renderState(state: RenameViewModel.UiState) {
        when (state) {
            is RenameViewModel.UiState.Idle -> {
                binding.progress.visibility = View.GONE
                binding.tvStage.text = "就绪"
                binding.btnStart.isEnabled = true
                binding.btnReset.isEnabled = true
            }
            is RenameViewModel.UiState.Running -> {
                binding.progress.visibility = View.VISIBLE
                binding.progress.progress = state.percent
                binding.tvStage.text = "${state.stage} (${state.percent}%)"
                binding.btnStart.isEnabled = false
                binding.btnReset.isEnabled = false
            }
            is RenameViewModel.UiState.NeedSaveLocation -> {
                binding.progress.visibility = View.VISIBLE
                binding.progress.progress = 95
                binding.tvStage.text = "请选择保存位置…"
                binding.btnStart.isEnabled = false
                saveApkLauncher.launch(state.suggestedFilename)
            }
            is RenameViewModel.UiState.Done -> {
                binding.progress.visibility = View.GONE
                binding.tvStage.text = "完成"
                binding.btnStart.isEnabled = true
                binding.btnReset.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("改名成功")
                    .setMessage(
                        "显示名：${state.savedDisplayName}\n" +
                            "新包名：${state.packageName}\n\n" +
                            "已保存到你选择的位置。请用文件管理器打开它进行安装。\n\n" +
                            "提示：若原 APK 已安装，新包名的应用可以独立共存。"
                    )
                    .setPositiveButton("好") { _, _ -> vm.reset() }
                    .show()
            }
            is RenameViewModel.UiState.Failed -> {
                binding.progress.visibility = View.GONE
                binding.tvStage.text = "失败：${state.stage}"
                binding.btnStart.isEnabled = true
                binding.btnReset.isEnabled = true
                AlertDialog.Builder(this)
                    .setTitle("出错了（${state.stage}）")
                    .setMessage(state.message + "\n\n常见原因：\n• 加固/混淆的 APK 不支持\n• APK 有 v2 签名校验且应用内做了二次校验\n• 文件已损坏")
                    .setPositiveButton("知道了", null)
                    .show()
            }
        }
    }

    private fun queryDisplayName(uri: Uri): String {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) return cursor.getString(idx) ?: uri.lastPathSegment.orEmpty()
                }
            }
        return uri.lastPathSegment.orEmpty()
    }
}
