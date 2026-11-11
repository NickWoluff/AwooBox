package com.nickwoluff.awoobox

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Refresh
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
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class RootSettingsActivity : ComponentActivity() {
    private var state by mutableStateOf(RootState.UNAVAILABLE)
    private var detail by mutableStateOf("正在检查 Root 授权状态。")
    private var checking by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AwooThemeHost(ThemeStore(this)) { preferences, _ ->
                SystemBackHandler(preferences.predictiveBack, ::finish)
                RootSettingsScreen(
                    state = state,
                    detail = detail,
                    checking = checking,
                    onBack = ::finish,
                    onRefresh = ::refresh,
                    onOpenManager = { openRootManager(this@RootSettingsActivity) },
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        if (checking) return
        checking = true
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) { ShellProbe.checkRoot() }
            state = result.first
            detail = result.second.ifBlank {
                if (result.first == RootState.GRANTED) {
                    "嗷呜工具箱已获得 Root 权限，可以读取并修复 adbd 属性标签。"
                } else {
                    "尚未获得 Root 授权。"
                }
            }
            checking = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun RootSettingsScreen(
    state: RootState,
    detail: String,
    checking: Boolean,
    onBack: () -> Unit,
    onRefresh: () -> Unit,
    onOpenManager: () -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = { Text("Root 权限") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = !checking) {
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
                RootStatusCard(state = state, detail = detail, checking = checking)
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
                                Icons.Filled.AdminPanelSettings,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.size(12.dp))
                            Text("授权管理", style = MaterialTheme.typography.titleLarge)
                        }
                        Text(
                            "嗷呜工具箱不会绕过 Root 管理器。首次使用或授权被撤销后，请在 KernelSU / Magisk 的超级用户列表中允许本应用。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(onClick = onOpenManager) {
                                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null)
                                Spacer(Modifier.size(8.dp))
                                Text("打开 Root 管理器")
                            }
                            if (state != RootState.GRANTED) {
                                Button(onClick = onRefresh, enabled = !checking) {
                                    Text("我已授权")
                                }
                            }
                        }
                    }
                }
            }
            item {
                Text(
                    "Root 权限用于使用需要 Root 的相关功能；系统组件管理优先通过 Shizuku UserService 执行。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun RootStatusCard(
    state: RootState,
    detail: String,
    checking: Boolean,
) {
    val granted = state == RootState.GRANTED
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
    val icon = if (granted) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline
    val title = when {
        checking -> "正在检查 Root"
        granted -> "已获得 Root 权限"
        state == RootState.NOT_GRANTED -> "Root 权限未授予"
        else -> "未检测到 Root"
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
            Text(detail, color = content.copy(alpha = 0.82f))
        }
    }
}
