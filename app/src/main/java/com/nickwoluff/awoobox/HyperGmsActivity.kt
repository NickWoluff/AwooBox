package com.nickwoluff.awoobox

import android.content.ComponentName
import android.content.ServiceConnection
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.IBinder
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.nickwoluff.awoobox.shizuku.PrivilegedService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
class HyperGmsActivity : ComponentActivity() {

    private var privilegeState by mutableStateOf(PrivilegeState.Waiting)
    private var snapshot by mutableStateOf<SystemSnapshot?>(null)
    private var busy by mutableStateOf(false)
    private var message by mutableStateOf("")
    private var pendingEnabledState by mutableStateOf<Boolean?>(null)
    private var remoteService: IHyperGmsService? = null
    private var bindRequested = false

    private val binderReceivedListener = Shizuku.OnBinderReceivedListener {
        handleBinderReady()
    }
    private val binderDeadListener = Shizuku.OnBinderDeadListener {
        remoteService = null
        bindRequested = false
        privilegeState = PrivilegeState.Unavailable
        message = "Shizuku 服务已停止，请启动后重新授权。"
    }
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            remoteService = IHyperGmsService.Stub.asInterface(binder)
            privilegeState = PrivilegeState.Ready
            refreshStatus()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            remoteService = null
            bindRequested = false
            privilegeState = if (Shizuku.pingBinder()) PrivilegeState.Connecting else PrivilegeState.Unavailable
        }
    }

    private val userServiceArgs by lazy {
        Shizuku.UserServiceArgs(
            ComponentName(BuildConfig.APPLICATION_ID, PrivilegedService::class.java.name),
        )
            .daemon(false)
            .processNameSuffix("privileged")
            .debuggable(BuildConfig.DEBUG)
            .version(BuildConfig.VERSION_CODE)
            .tag("hypergms-control")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        Shizuku.addBinderReceivedListenerSticky(binderReceivedListener)
        Shizuku.addBinderDeadListener(binderDeadListener)

        val themePreferences = ThemeStore(this).load()
        setContent {
            AwooTheme(themePreferences) {
                SystemBackHandler(themePreferences.predictiveBack, ::finish)
                HyperGmsScreen(
                    privilegeState = privilegeState,
                    snapshot = snapshot,
                    busy = busy,
                    message = message,
                    onRefresh = ::refreshStatus,
                    onChangeReceiver = { pendingEnabledState = it },
                )

                val targetState = pendingEnabledState
                if (targetState != null) {
                    ReceiverActionDialog(
                        enable = targetState,
                        onConfirm = {
                            pendingEnabledState = null
                            setReceiverEnabled(targetState)
                        },
                        onDismiss = { pendingEnabledState = null },
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (remoteService != null && !busy) {
            refreshStatus()
        } else if (Shizuku.pingBinder()) {
            handleBinderReady()
        }
    }

    override fun onDestroy() {
        Shizuku.removeBinderReceivedListener(binderReceivedListener)
        Shizuku.removeBinderDeadListener(binderDeadListener)
        if (bindRequested && Shizuku.pingBinder()) {
            runCatching { Shizuku.unbindUserService(userServiceArgs, serviceConnection, false) }
        }
        super.onDestroy()
    }

    private fun handleBinderReady() {
        if (Shizuku.isPreV11()) {
            privilegeState = PrivilegeState.Unsupported
            message = "需要 Shizuku 11 或更高版本。"
            return
        }
        privilegeState = try {
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) {
                bindPrivilegedService()
                PrivilegeState.Connecting
            } else {
                PrivilegeState.PermissionRequired
            }
        } catch (error: Throwable) {
            message = error.message ?: "无法连接 Shizuku"
            PrivilegeState.Unavailable
        }
    }

    private fun bindPrivilegedService() {
        if (bindRequested) return
        bindRequested = true
        privilegeState = PrivilegeState.Connecting
        try {
            Shizuku.bindUserService(userServiceArgs, serviceConnection)
        } catch (error: Throwable) {
            bindRequested = false
            privilegeState = PrivilegeState.Error
            message = error.message ?: "无法启动 Shizuku UserService"
        }
    }

    private fun refreshStatus() {
        val service = remoteService ?: run {
            if (privilegeState == PrivilegeState.Ready) privilegeState = PrivilegeState.Connecting
            return
        }
        if (busy) return
        busy = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) { SystemSnapshot.fromJson(service.status) }
            }.onSuccess { current ->
                snapshot = current
                message = current.queryError
            }.onFailure { error ->
                message = error.message ?: "查询系统状态失败"
            }
            busy = false
        }
    }

    private fun setReceiverEnabled(enabled: Boolean) {
        val service = remoteService ?: return
        if (busy) return
        busy = true
        lifecycleScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    SystemSnapshot.fromJson(service.setReceiverEnabled(enabled))
                }
            }.onSuccess { current ->
                snapshot = current
                message = when {
                    current.operationExitCode != null && current.operationExitCode != 0 ->
                        current.operationFailureMessage().orEmpty()
                    enabled && current.componentState in setOf(ComponentState.DEFAULT, ComponentState.ENABLED) ->
                        "已恢复 CleanerReceiver 的 Manifest 默认状态。"
                    !enabled && current.componentState in setOf(ComponentState.DISABLED, ComponentState.DISABLED_USER) ->
                        "已停用 CleanerReceiver，自动关闭 GMS 的广播将被拦截。"
                    else -> "命令已执行，但组件状态与预期不一致，请刷新后复核。"
                }
            }.onFailure { error ->
                message = error.message ?: "修改组件状态失败"
            }
            busy = false
        }
    }

    companion object {
    }
}

private enum class PrivilegeState {
    Waiting,
    Unavailable,
    PermissionRequired,
    Denied,
    Connecting,
    Ready,
    Unsupported,
    Error,
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun HyperGmsScreen(
    privilegeState: PrivilegeState,
    snapshot: SystemSnapshot?,
    busy: Boolean,
    message: String,
    onRefresh: () -> Unit,
    onChangeReceiver: (Boolean) -> Unit,
) {
    Scaffold(
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("拦截 GMS 自动关闭", fontWeight = FontWeight.Bold)
                        Text(
                            text = "管理手机管家 CleanerReceiver 自动任务",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                actions = {
                    IconButton(onClick = onRefresh, enabled = privilegeState == PrivilegeState.Ready && !busy) {
                        Icon(Icons.Filled.Refresh, contentDescription = "刷新状态")
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                OverviewCard(snapshot = snapshot, privilegeState = privilegeState, busy = busy)
            }

            if (privilegeState != PrivilegeState.Ready) item {
                PermissionHintCard(state = privilegeState)
            }

            if (snapshot != null) {
                item { ReceiverCard(snapshot = snapshot) }
                item { AlarmCard(snapshot = snapshot) }
                item {
                    ActionCard(
                        snapshot = snapshot,
                        busy = busy,
                        onChangeReceiver = onChangeReceiver,
                    )
                }
            }

            if (message.isNotBlank()) {
                item { MessageCard(message) }
            }

            item { ScopeCard() }
        }
    }
}

@Composable
private fun OverviewCard(
    snapshot: SystemSnapshot?,
    privilegeState: PrivilegeState,
    busy: Boolean,
) {
    val protection = snapshot?.effectiveProtection
    val container = when (protection) {
        ProtectionState.PROTECTED -> MaterialTheme.colorScheme.primaryContainer
        ProtectionState.EXPOSED -> MaterialTheme.colorScheme.errorContainer
        ProtectionState.NO_TASK -> MaterialTheme.colorScheme.tertiaryContainer
        else -> MaterialTheme.colorScheme.surfaceContainerHigh
    }
    val content = when (protection) {
        ProtectionState.PROTECTED -> MaterialTheme.colorScheme.onPrimaryContainer
        ProtectionState.EXPOSED -> MaterialTheme.colorScheme.onErrorContainer
        ProtectionState.NO_TASK -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }
    val icon = when (protection) {
        ProtectionState.PROTECTED -> Icons.Filled.GppGood
        ProtectionState.EXPOSED -> Icons.Filled.ErrorOutline
        ProtectionState.NO_TASK -> Icons.Filled.CheckCircle
        else -> Icons.Filled.Shield
    }
    val title = when {
        busy -> "正在读取系统状态"
        privilegeState != PrivilegeState.Ready -> "等待 Shizuku 授权"
        protection == ProtectionState.PROTECTED -> "自动关闭已拦截"
        protection == ProtectionState.EXPOSED -> "发现可执行的自动任务"
        protection == ProtectionState.NO_TASK -> "当前未发现自动任务"
        protection == ProtectionState.UNSUPPORTED -> "未找到小米手机管家"
        else -> "状态暂不可用"
    }
    val subtitle = when {
        busy -> "正在核对组件状态和 AlarmManager 计划…"
        protection == ProtectionState.PROTECTED && snapshot.taskPresent ->
            "闹钟仍登记，但接收器已停用，系统无法向其投递。"
        protection == ProtectionState.PROTECTED -> "CleanerReceiver 已停用。"
        protection == ProtectionState.EXPOSED -> "CleanerReceiver 已启用，且每日 RTC 任务仍登记。"
        protection == ProtectionState.NO_TASK -> "接收器仍启用，手机管家以后可能重新注册任务。"
        else -> "授权后可查询并管理 CleanerReceiver。"
    }

    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = container, contentColor = content),
        shape = RoundedCornerShape(36.dp),
    ) {
        Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Surface(
                color = content.copy(alpha = 0.12f),
                contentColor = content,
                shape = RoundedCornerShape(24.dp),
            ) {
                Box(Modifier.size(64.dp), contentAlignment = Alignment.Center) {
                    if (busy) {
                        CircularProgressIndicator(Modifier.size(30.dp), strokeWidth = 3.dp, color = content)
                    } else {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(34.dp))
                    }
                }
            }
            AnimatedContent(targetState = title, label = "overview-status") { currentTitle ->
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(currentTitle, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                    Text(subtitle, style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
    }
}

@Composable
private fun PermissionHintCard(state: PrivilegeState) {
    InfoCard(
        icon = Icons.Filled.Shield,
        title = "需要 Shizuku 权限",
    ) {
        val detail = when (state) {
            PrivilegeState.Unavailable -> "未检测到正在运行的 Shizuku 服务。"
            PrivilegeState.PermissionRequired -> "Shizuku 已运行，但嗷呜工具箱尚未获得授权。"
            PrivilegeState.Denied -> "Shizuku 权限未授予，当前不会执行任何系统操作。"
            PrivilegeState.Connecting -> "正在启动受权 UserService…"
            PrivilegeState.Unsupported -> "当前 Shizuku 版本过低，需要版本 11 或更高。"
            PrivilegeState.Error -> "连接 UserService 失败。"
            else -> "正在等待 Shizuku Binder…"
        }
        Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text("请前往 设置 → Shizuku 权限 完成授权后返回本页。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun ReceiverCard(snapshot: SystemSnapshot) {
    InfoCard(icon = Icons.Filled.PowerSettingsNew, title = "接收器状态") {
        StatusRow(
            label = "CleanerReceiver",
            value = when (snapshot.componentState) {
                ComponentState.DEFAULT, ComponentState.ENABLED -> "启用"
                ComponentState.DISABLED, ComponentState.DISABLED_USER -> "已停用"
                ComponentState.UNKNOWN -> "未知"
            },
            emphasized = true,
        )
        StatusRow(
            label = "包管理器状态",
            value = when (snapshot.componentState) {
                ComponentState.DEFAULT -> "Manifest 默认"
                ComponentState.ENABLED -> "显式启用"
                ComponentState.DISABLED_USER -> "用户停用"
                ComponentState.DISABLED -> "停用"
                ComponentState.UNKNOWN -> "未知"
            },
        )
        StatusRow("Shizuku 执行身份", snapshot.executionModeLabel())
        Text(
            text = "目标：com.miui.securitycenter/.receiver.CleanerReceiver",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AlarmCard(snapshot: SystemSnapshot) {
    InfoCard(icon = Icons.Filled.Alarm, title = "自动停用任务") {
        StatusRow("当前登记", if (snapshot.taskPresent) "存在" else "未发现", emphasized = true)
        if (snapshot.alarms.isEmpty()) {
            Text(
                "AlarmManager 当前没有 CleanerReceiver 的活动条目。手机管家 CacheService 以后仍可能重新登记。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            snapshot.alarms.forEachIndexed { index, alarm ->
                if (index > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                StatusRow("最早触发", alarm.displayStart())
                if (alarm.window != "0") StatusRow("最晚投递", alarm.displayEnd())
                StatusRow("调度方式", "${alarm.type} · ${alarm.displayWindow()}")
                StatusRow("重复周期", alarm.displayRepeat())
            }
            Text(
                "RTC 任务按系统墙上时间调度，非 RTC_WAKEUP 时不会主动唤醒深度休眠设备。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (snapshot.checkedAt > 0L) {
            Text(
                "查询于 ${formatTimestamp(snapshot.checkedAt)}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ActionCard(
    snapshot: SystemSnapshot,
    busy: Boolean,
    onChangeReceiver: (Boolean) -> Unit,
) {
    InfoCard(icon = Icons.Filled.Schedule, title = "保护控制") {
        if (snapshot.componentState == ComponentState.UNKNOWN) {
            Text("无法可靠判断组件状态，因此不会提供修改按钮。请确认 Shizuku 正常后刷新。")
        } else if (snapshot.componentEnabled) {
            Text(
                if (snapshot.executionUid == 2000) {
                    "当前为 ADB/Shell 模式。此 HyperOS 版本通常禁止 Shell 修改系统手机管家组件；需要 Root Shizuku 才能执行停用。"
                } else {
                    "停用后，每日闹钟即使仍登记，也无法投递给 CleanerReceiver。"
                },
            )
            Button(
                onClick = { onChangeReceiver(false) },
                enabled = !busy && snapshot.packageInstalled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.Shield, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("停用自动关闭任务")
            }
        } else {
            Text("恢复后，手机管家可再次接收现有或以后重新登记的 CleanerReceiver 闹钟。")
            Button(
                onClick = { onChangeReceiver(true) },
                enabled = !busy && snapshot.packageInstalled,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Filled.PowerSettingsNew, contentDescription = null)
                Spacer(Modifier.size(8.dp))
                Text("恢复接收器默认状态")
            }
        }
    }
}

@Composable
private fun MessageCard(message: String) {
    ElevatedCard(
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = RoundedCornerShape(24.dp),
    ) {
        Row(
            Modifier.padding(18.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            Text(message, color = MaterialTheme.colorScheme.onSecondaryContainer)
        }
    }
}

@Composable
private fun ScopeCard() {
    InfoCard(icon = Icons.Filled.CloudSync, title = "影响范围") {
        Text("停用的是手机管家的私有 CleanerReceiver，不是整个手机管家。")
        Text("一同停止：GMS 自动检查、优化管理动画云控刷新、快捷回复默认名单刷新。")
        Text("不直接停用：病毒扫描、垃圾清理、省电、应用管理和游戏工具箱主体功能。")
        Text("系统或手机管家更新后，建议重新打开本功能复核状态。")
    }
}

@Composable
private fun InfoCard(
    icon: ImageVector,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    ElevatedCard(
        shape = RoundedCornerShape(30.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Box(Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                        Icon(icon, contentDescription = null, modifier = Modifier.size(24.dp))
                    }
                }
                Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun StatusRow(label: String, value: String, emphasized: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(
            text = label,
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = value,
            fontWeight = if (emphasized) FontWeight.Bold else FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun ReceiverActionDialog(
    enable: Boolean,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (enable) Icons.Filled.PowerSettingsNew else Icons.Filled.Shield,
                contentDescription = null,
            )
        },
        title = { Text(if (enable) "恢复 CleanerReceiver？" else "停用 CleanerReceiver？") },
        text = {
            Text(
                if (enable) {
                    "将恢复组件的 Manifest 默认状态。现有或以后重新登记的每日任务将可以再次投递。"
                } else {
                    "这会拦截 GMS 自动关闭，同时停止优化管理动画云控与快捷回复默认名单的每日刷新。不会停用整个手机管家。"
                },
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = if (enable) {
                    ButtonDefaults.buttonColors()
                } else {
                    ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError,
                    )
                },
            ) {
                Text(if (enable) "确认恢复" else "确认停用")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = RoundedCornerShape(32.dp),
    )
}

private fun formatTimestamp(epochMillis: Long): String {
    val formatter = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
        .withLocale(Locale.SIMPLIFIED_CHINESE)
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.systemDefault())
        .format(formatter)
}




