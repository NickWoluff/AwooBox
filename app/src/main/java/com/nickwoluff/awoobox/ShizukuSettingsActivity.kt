package com.nickwoluff.awoobox

import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SettingsApplications
import androidx.compose.material3.Button
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import rikka.shizuku.Shizuku

private const val SHIZUKU_PERMISSION_REQUEST_CODE = 2001
private const val SHIZUKU_PACKAGE_NAME = "moe.shizuku.privileged.api"

class ShizukuSettingsActivity : ComponentActivity() {
    private var state by mutableStateOf(ShizukuPermissionState.Waiting)
    private var message by mutableStateOf("")

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        refreshPermissionState()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        state = ShizukuPermissionState.Unavailable
        message = "Shizuku 服务已停止，请重新启动 Shizuku。"
    }
    private val permissionResultListener = Shizuku.OnRequestPermissionResultListener { requestCode, grantResult ->
        if (requestCode != SHIZUKU_PERMISSION_REQUEST_CODE) return@OnRequestPermissionResultListener
        if (grantResult == PackageManager.PERMISSION_GRANTED) {
            state = ShizukuPermissionState.Granted
            message = "嗷呜工具箱已获得 Shizuku 权限，可以使用需要此权限的相关功能。"
        } else {
            state = ShizukuPermissionState.Denied
            message = "Shizuku 权限未授予。请在 Shizuku 管理器的应用授权中允许嗷呜工具箱。"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)
        Shizuku.addRequestPermissionResultListener(permissionResultListener)
        setContent {
            AwooThemeHost(ThemeStore(this)) { preferences, _ ->
                SystemBackHandler(preferences.predictiveBack, ::finish)
                ShizukuSettingsScreen(
                    state = state,
                    message = message,
                    onBack = ::finish,
                    onRefresh = ::refreshPermissionState,
                    onRequestPermission = ::requestPermission,
                    onOpenShizuku = ::openShizuku,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refreshPermissionState()
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        Shizuku.removeRequestPermissionResultListener(permissionResultListener)
        super.onDestroy()
    }

    private fun refreshPermissionState() {
        state = try {
            when {
                Shizuku.isPreV11() -> ShizukuPermissionState.Unsupported
                !Shizuku.pingBinder() -> ShizukuPermissionState.Unavailable
                Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED ->
                    ShizukuPermissionState.Granted
                Shizuku.shouldShowRequestPermissionRationale() -> ShizukuPermissionState.Denied
                else -> ShizukuPermissionState.PermissionRequired
            }
        } catch (error: Throwable) {
            message = error.message ?: "读取 Shizuku 授权状态失败。"
            ShizukuPermissionState.Error
        }
        if (state == ShizukuPermissionState.Granted) {
            message = "嗷呜工具箱已获得 Shizuku 权限，可以使用需要此权限的相关功能。"
        }
    }

    private fun requestPermission() {
        if (!Shizuku.pingBinder()) {
            state = ShizukuPermissionState.Unavailable
            message = "请先在 Shizuku 中启动服务。"
            return
        }
        if (Shizuku.shouldShowRequestPermissionRationale()) {
            state = ShizukuPermissionState.Denied
            message = "Shizuku 权限未授予。请在 Shizuku 管理器的应用授权中允许嗷呜工具箱。"
            return
        }
        runCatching { Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE) }
            .onFailure {
                state = ShizukuPermissionState.Error
                message = it.message ?: "无法请求 Shizuku 授权。"
            }
    }

    private fun openShizuku() {
        val intent = packageManager.getLaunchIntentForPackage(SHIZUKU_PACKAGE_NAME)
        if (intent == null) {
            message = "未找到 Shizuku，请先安装并启动 Shizuku。"
        } else {
            startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }
    }
}

private enum class ShizukuPermissionState {
    Waiting,
    Unavailable,
    PermissionRequired,
    Denied,
    Granted,
    Unsupported,
    Error,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun ShizukuSettingsScreen(
    state: ShizukuPermissionState,
    message: String,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenShizuku: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Shizuku 权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新检测")
                    }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ShizukuStatusCard(state = state, message = message)
            }
            item {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(28.dp),
                    colors = CardDefaults.elevatedCardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer,
                    ),
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.SettingsApplications,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text("授权管理", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "获得 Shizuku 权限后，嗷呜工具箱可以使用需要此权限的相关功能。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            if (state == ShizukuPermissionState.PermissionRequired || state == ShizukuPermissionState.Denied) {
                                Button(onClick = onRequestPermission) {
                                    Text("请求授权")
                                }
                            }
                            OutlinedButton(onClick = onOpenShizuku) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("打开 Shizuku")
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Shizuku 服务由 Shizuku 管理器启动；本页只管理嗷呜工具箱的 Shizuku 应用授权。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ShizukuStatusCard(
    state: ShizukuPermissionState,
    message: String,
) {
    val granted = state == ShizukuPermissionState.Granted
    val container = if (granted) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = if (granted) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val icon = if (granted) Icons.Filled.CheckCircle else Icons.Filled.AdminPanelSettings
    val title = when (state) {
        ShizukuPermissionState.Granted -> "已获得 Shizuku 权限"
        ShizukuPermissionState.Unavailable -> "Shizuku 未运行"
        ShizukuPermissionState.PermissionRequired -> "Shizuku 权限未授予"
        ShizukuPermissionState.Denied -> "Shizuku 权限未授予"
        ShizukuPermissionState.Unsupported -> "Shizuku 版本过低"
        ShizukuPermissionState.Error -> "无法读取授权状态"
        ShizukuPermissionState.Waiting -> "正在检查 Shizuku"
    }
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.size(14.dp))
                Text(title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            }
            Text(
                message.ifBlank { "正在读取 Shizuku 服务和应用授权状态。" },
                color = content.copy(alpha = 0.82f),
            )
        }
    }
}
