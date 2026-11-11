package com.nickwoluff.awoobox

import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.activity.compose.PredictiveBackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwipeLeft
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.DropdownMenuGroup
import androidx.compose.material3.DropdownMenuPopup
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collect

private enum class RootTab(val label: String) { FEATURES("功能"), SETTINGS("设置") }
private const val ADBD_CORRECT = "u:object_r:adbd_config_prop:s0"
private const val SHELL_CORRECT = "u:object_r:shell_prop:s0"
private enum class FeatureId(val title: String, val summary: String) {
    ADB_LABEL("修复 adbd 属性标签", "恢复 adbd_config_prop 的 SELinux 标签，让 TCP 模式重新可用。"),
    HYPER_GMS("拦截 GMS 自动关闭", "管理小米手机管家 CleanerReceiver，阻止 GMS 每日自动关闭。"),
}

private const val EXTRA_FEATURE = "com.nickwoluff.awoobox.extra.FEATURE"

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AwooBoxApp(
    themePreferences: ThemePreferences,
    onThemePreferencesChange: (ThemePreferences) -> Unit,
) {
    val context = LocalContext.current
    var selectedTab by rememberSaveable { mutableStateOf(RootTab.FEATURES) }
    val pagerState = rememberPagerState(
        initialPage = selectedTab.ordinal,
        pageCount = { RootTab.entries.size },
    )
    val scope = rememberCoroutineScope()

    LaunchedEffect(pagerState.currentPage) {
        selectedTab = RootTab.entries[pagerState.currentPage]
    }
    LaunchedEffect(selectedTab) {
        if (!pagerState.isScrollInProgress && pagerState.currentPage != selectedTab.ordinal) {
            pagerState.scrollToPage(selectedTab.ordinal)
        }
    }
    val selectTab: (RootTab) -> Unit = { tab ->
        scope.launch { pagerState.animateScrollToPage(tab.ordinal) }
    }

    Scaffold(
        // 内层页面各自管理状态栏与顶栏；底栏保留默认导航栏 inset，为手势提示线留出安全区。
        contentWindowInsets = androidx.compose.foundation.layout.WindowInsets(0, 0, 0, 0),
        bottomBar = {
            NavigationBar(
                containerColor = MaterialTheme.colorScheme.background,
                tonalElevation = 0.dp,
            ) {
                NavigationBarItem(
                    selected = pagerState.currentPage == RootTab.FEATURES.ordinal,
                    onClick = { selectTab(RootTab.FEATURES) },
                    icon = { Icon(Icons.Filled.Home, contentDescription = "功能") },
                    label = { Text(RootTab.FEATURES.label) },
                )
                NavigationBarItem(
                    selected = pagerState.currentPage == RootTab.SETTINGS.ordinal,
                    onClick = { selectTab(RootTab.SETTINGS) },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text(RootTab.SETTINGS.label) },
                )
            }
        },
    ) { padding ->
        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) { page ->
            when (RootTab.entries[page]) {
                RootTab.FEATURES -> FeatureListScreen(
                    onOpen = { feature ->
                        if (feature == FeatureId.HYPER_GMS) {
                            context.startActivity(Intent(context, HyperGmsActivity::class.java))
                        } else {
                            context.startActivity(
                                Intent(context, FeatureDetailActivity::class.java)
                                    .putExtra(EXTRA_FEATURE, feature.name),
                            )
                        }
                    },
                )
                RootTab.SETTINGS -> SettingsHomeScreen(
                    preferences = themePreferences,
                    onOpenTheme = {
                        context.startActivity(Intent(context, ThemeSettingsActivity::class.java))
                    },
                    onOpenShizuku = {
                        context.startActivity(Intent(context, ShizukuSettingsActivity::class.java))
                    },
                    onOpenRoot = {
                        context.startActivity(Intent(context, RootSettingsActivity::class.java))
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeatureListScreen(onOpen: (FeatureId) -> Unit) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("嗷呜工具箱") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item { Text("选择一个功能开始", style = MaterialTheme.typography.titleMedium) }
            items(FeatureId.entries, key = { it.name }) { feature ->
                AwooCard(
                    modifier = Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable { onOpen(feature) },
                ) {
                    Row(
                        modifier = Modifier.padding(18.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        Box(
                            modifier = Modifier.size(52.dp).clip(MaterialTheme.shapes.largeIncreased)
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                if (feature == FeatureId.HYPER_GMS) Icons.Filled.CloudSync else Icons.Filled.Shield,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(26.dp),
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(feature.title, style = MaterialTheme.typography.titleMedium)
                            Spacer(Modifier.height(3.dp))
                            Text(feature.summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Icon(Icons.Filled.ChevronRight, contentDescription = "进入功能", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun FeatureDetailScreen(
    feature: FeatureId,
    onBack: () -> Unit,
    snackbar: SnackbarHostState,
    scope: kotlinx.coroutines.CoroutineScope,
    openManager: () -> Unit,
) {
    var probe by remember { mutableStateOf<ProbeResult?>(null) }
    var loading by remember { mutableStateOf(true) }
    var repairing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<RepairResult?>(null) }
    var fixShellProp by rememberSaveable { mutableStateOf(false) }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    suspend fun refresh() {
        loading = true
        probe = withContext(Dispatchers.IO) { ShellProbe.probe() }
        loading = false
    }

    LaunchedEffect(Unit) { refresh() }

    fun runRepair() {
        scope.launch {
            repairing = true
            result = null
            val (rootState, _) = withContext(Dispatchers.IO) { ShellProbe.checkRoot() }
            if (rootState != RootState.GRANTED) {
                repairing = false
                probe = withContext(Dispatchers.IO) { ShellProbe.probe() }
                snackbar.showSnackbar("未获得超级用户权限，未执行修复")
                return@launch
            }
            result = withContext(Dispatchers.IO) { ShellProbe.repairLabels(fixShellProp) }
            repairing = false
            if (result?.success == true) {
                refresh()
                snackbar.showSnackbar("属性标签已修复")
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text(feature.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { scope.launch { refresh() } }, enabled = !loading && !repairing) {
                        Icon(Icons.Filled.Refresh, contentDescription = "重新检测")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Text(
                    "只修复 SELinux 属性标签，不设置端口、不重启 adbd。修复后由 Shizuku / Stellar 自行启用 TCP。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                AwooCard {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("当前状态", style = MaterialTheme.typography.titleMedium)
                            if (loading) LoadingIndicator(modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.height(12.dp))
                        val p = probe
                        if (loading && p == null) {
                            Text("正在检查 root 授权与系统状态…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        } else if (p != null) {
                            when (p.rootState) {
                                RootState.GRANTED -> {
                                    StatusLine("超级用户", "已授权", true)
                                    HorizontalDivider(Modifier.padding(vertical = 8.dp))
                                    StatusLine("adbd_config_prop", p.adbdLabel, p.adbdLabel == ADBD_CORRECT)
                                    StatusLine("shell_prop", p.shellLabel, p.shellLabel == SHELL_CORRECT)
                                    StatusLine("TCP 端口", p.tcpPort.ifBlank { "未启用" })
                                }
                                RootState.NOT_GRANTED, RootState.UNAVAILABLE -> {
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        Icon(Icons.Filled.AdminPanelSettings, null, tint = MaterialTheme.colorScheme.error)
                                        Text(p.detail ?: "尚未获得超级用户权限。", color = MaterialTheme.colorScheme.error)
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    Text("请打开 KernelSU / Magisk 管理器，在超级用户列表中允许“嗷呜工具箱”，再回来重新检测。", style = MaterialTheme.typography.bodyMedium)
                                    Spacer(Modifier.height(10.dp))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        OutlinedButton(onClick = openManager) {
                                            Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                                            Spacer(Modifier.width(6.dp))
                                            Text("打开 root 管理器")
                                        }
                                        TextButton(onClick = { scope.launch { refresh() } }) { Text("我已授权") }
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                AwooCard {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        Text("修复选项", style = MaterialTheme.typography.titleMedium)
                        SettingsSwitchRow(
                            icon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                            title = "同时修复 shell_prop",
                            subtitle = "可选。主要影响 adb root / simpleperf，不影响 TCP。",
                            checked = fixShellProp,
                            onCheckedChange = { fixShellProp = it },
                        )
                        Button(
                            onClick = ::runRepair,
                            enabled = !repairing && !loading && probe?.rootState == RootState.GRANTED,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            if (repairing) {
                                LoadingIndicator(modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.onPrimary)
                            } else {
                                Icon(Icons.Filled.Security, null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(if (repairing) "正在修复…" else "修复属性标签")
                        }
                    }
                }
            }
            item {
                AnimatedVisibility(
                    visible = result != null,
                    enter = fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = spring(dampingRatio = Spring.DampingRatioNoBouncy)),
                    exit = fadeOut(tween(140)) + scaleOut(targetScale = 0.98f, animationSpec = tween(140)),
                ) {
                    val r = result ?: return@AnimatedVisibility
                    AwooCard(
                        modifier = Modifier.fillMaxWidth(),
                        containerColor = if (r.success) MaterialTheme.colorScheme.tertiaryContainer
                        else MaterialTheme.colorScheme.errorContainer,
                    ) {
                        Column(Modifier.padding(18.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(if (r.success) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline, null)
                                Text(if (r.success) "修复完成" else "修复未完成", style = MaterialTheme.typography.titleMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(r.log, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SettingsHomeScreen(
    preferences: ThemePreferences,
    onOpenTheme: () -> Unit,
    onOpenShizuku: () -> Unit,
    onOpenRoot: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("设置") },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            SettingsSection("权限与服务") {
                SettingsActionRow(
                    icon = { Icon(Icons.Filled.AdminPanelSettings, contentDescription = null) },
                    title = "Shizuku 权限",
                    subtitle = "检查并管理嗷呜工具箱的 Shizuku 授权",
                    onClick = onOpenShizuku,
                )
                SettingsDivider()
                SettingsActionRow(
                    icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                    title = "Root 权限",
                    subtitle = "检查并管理嗷呜工具箱的 Root 授权",
                    onClick = onOpenRoot,
                )
            }
            SettingsSection("外观") {
                SettingsActionRow(
                    icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                    title = "主题设置",
                    subtitle = "主题模式、动态取色与配色",
                    trailingText = preferences.mode.label,
                    onClick = onOpenTheme,
                )
            }
            SettingsSection("关于") {
                SettingsActionRow(
                    icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                    title = "嗷呜工具箱",
                    subtitle = "AwooBox",
                    trailingText = BuildConfig.VERSION_NAME,
                    onClick = null,
                    showChevron = false,
                )
            }
            Text(
                text = buildString {
                    val currentYear = java.time.Year.now().value
                    append("© 2026")
                    if (currentYear > 2026) append('-').append(currentYear)
                    append(" Nick Woluff. All Rights Reserved.")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 24.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ThemeSettingsScreen(
    preferences: ThemePreferences,
    onChange: (ThemePreferences) -> Unit,
    onBack: () -> Unit,
) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = { Text("主题设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    scrolledContainerColor = MaterialTheme.colorScheme.background,
                ),
                scrollBehavior = scrollBehavior,
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            item {
                SettingsCard {
                    SettingsDropdownRow(
                        icon = { Icon(Icons.Filled.DarkMode, contentDescription = null) },
                        title = "主题",
                        summary = preferences.mode.label,
                        items = ThemeMode.entries.map { it.label },
                        selectedIndex = preferences.mode.ordinal,
                        onSelected = { onChange(preferences.copy(mode = ThemeMode.entries[it])) },
                    )
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = { Icon(Icons.Filled.ColorLens, contentDescription = null) },
                        title = "动态取色",
                        subtitle = "使用系统壁纸生成 Material 3 配色",
                        checked = preferences.dynamicColor,
                        onCheckedChange = { onChange(preferences.copy(dynamicColor = it)) },
                    )
                    SettingsDivider()
                    SettingsDropdownRow(
                        icon = { Icon(Icons.Filled.AutoAwesome, contentDescription = null) },
                        title = "调色板风格",
                        summary = preferences.paletteStyle.label,
                        items = PaletteStyle.entries.map { it.label },
                        selectedIndex = preferences.paletteStyle.ordinal,
                        onSelected = { onChange(preferences.copy(paletteStyle = PaletteStyle.entries[it])) },
                    )
                    AnimatedVisibility(
                        visible = !preferences.dynamicColor,
                        enter = expandVertically(expandFrom = Alignment.Top) + fadeIn(),
                        exit = shrinkVertically(shrinkTowards = Alignment.Top) + fadeOut(),
                    ) {
                        Column {
                            SettingsDivider()
                            SettingsDropdownRow(
                                icon = { Icon(Icons.Filled.Palette, contentDescription = null) },
                                title = "主题色",
                                summary = preferences.seedColor.label,
                                items = SeedColor.entries.map { it.label },
                                selectedIndex = preferences.seedColor.ordinal,
                                onSelected = { onChange(preferences.copy(seedColor = SeedColor.entries[it])) },
                            )
                        }
                    }
                    SettingsDivider()
                    SettingsSwitchRow(
                        icon = { Icon(Icons.Filled.SwipeLeft, contentDescription = null) },
                        title = "预测性返回手势",
                        subtitle = "使用系统预测性返回过渡动画",
                        checked = preferences.predictiveBack,
                        onCheckedChange = { onChange(preferences.copy(predictiveBack = it)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (title.isNotBlank()) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }
        SettingsCard(content)
    }
}

@Composable
private fun SettingsCard(content: @Composable () -> Unit) {
    Column(Modifier.fillMaxWidth().clip(MaterialTheme.shapes.large)) { content() }
}

@Composable
private fun SettingsDivider() {
    Spacer(Modifier.height(3.dp))
}

@Composable
private fun AwooCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLowest,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        content = content,
    )
}

@Composable
private fun SettingsActionRow(
    icon: (@Composable () -> Unit)?,
    title: String,
    subtitle: String? = null,
    trailingText: String? = null,
    onClick: (() -> Unit)?,
    showChevron: Boolean = true,
) {
    val hasSubtitle = subtitle?.isNotBlank() == true
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = if (hasSubtitle) 72.dp else 56.dp)
                .padding(horizontal = 16.dp, vertical = if (hasSubtitle) 12.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        it()
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                subtitle?.takeIf { it.isNotBlank() }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (trailingText != null || showChevron) {
                Spacer(Modifier.width(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    trailingText?.let {
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (showChevron) {
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    icon: (@Composable () -> Unit)?,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    val materialOnCheckedChange: (Boolean) -> Unit = { enabled ->
        hapticFeedback.performHapticFeedback(
            if (enabled) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff,
        )
        onCheckedChange(enabled)
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.extraSmall,
        color = MaterialTheme.colorScheme.surfaceContainerLowest,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.let {
                Box(contentAlignment = Alignment.Center) {
                    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurfaceVariant) {
                        it()
                    }
                }
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = materialOnCheckedChange)
        }
    }
}

@Composable
private fun SettingsDropdownRow(
    icon: (@Composable () -> Unit)?,
    title: String,
    summary: String,
    items: List<String>,
    selectedIndex: Int,
    onSelected: (Int) -> Unit,
) {
    val hapticFeedback = LocalHapticFeedback.current
    var expanded by remember { mutableStateOf(false) }
    var menuAnchor by remember { mutableStateOf(DpOffset.Zero) }
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxWidth()
            .pointerInput(title, items) {
                detectTapGestures { position ->
                    menuAnchor = with(density) { DpOffset(position.x.toDp(), position.y.toDp()) }
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    expanded = true
                }
            }
            .semantics {
                role = Role.Button
                onClick {
                    menuAnchor = DpOffset.Zero
                    hapticFeedback.performHapticFeedback(HapticFeedbackType.ContextClick)
                    expanded = true
                    true
                }
            },
    ) {
        SettingsActionRow(
            icon = icon,
            title = title,
            trailingText = summary,
            onClick = null,
            showChevron = false,
        )
        Box(
            Modifier
                .align(Alignment.TopStart)
                .offset {
                    IntOffset(menuAnchor.x.roundToPx(), menuAnchor.y.roundToPx())
                }
                .size(1.dp),
        ) {
            DropdownMenuPopup(
                expanded = expanded,
                onDismissRequest = { expanded = false },
            ) {
                DropdownMenuGroup(
                    shapes = MenuDefaults.groupShapes(),
                    containerColor = MenuDefaults.groupStandardContainerColor,
                ) {
                    items.forEachIndexed { index, item ->
                        DropdownMenuItem(
                            selected = index == selectedIndex,
                            text = { Text(item) },
                            onClick = {
                                hapticFeedback.performHapticFeedback(HapticFeedbackType.Confirm)
                                expanded = false
                                onSelected(index)
                            },
                            shapes = MenuDefaults.itemShape(index, items.size),
                            leadingIcon = { Spacer(Modifier.size(MenuDefaults.LeadingIconSize)) },
                            selectedLeadingIcon = {
                                Icon(
                                    Icons.Filled.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(MenuDefaults.LeadingIconSize),
                                )
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, good: Boolean? = null) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            if (good != null) {
                Icon(
                    if (good) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (good) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp),
                )
            }
            Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
        }
    }
}

internal fun openRootManager(context: android.content.Context) {
    val candidates = listOf("com.rifsxd.ksunext", "me.weishu.kernelsu", "com.topjohnwu.magisk")
    for (pkg in candidates) {
        val intent = context.packageManager.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            context.startActivity(intent)
            return
        }
    }
    context.startActivity(Intent(Settings.ACTION_APPLICATION_SETTINGS))
}

@Composable
internal fun AwooThemeHost(
    store: ThemeStore,
    content: @Composable (ThemePreferences, (ThemePreferences) -> Unit) -> Unit,
) {
    var preferences by remember { mutableStateOf(store.load()) }
    AwooTheme(preferences) {
        content(preferences) { updated ->
            preferences = updated
            store.save(updated)
        }
    }
}

/**
 * 使用 AndroidX 官方返回回调，不在页面内容上叠加位移、缩放或透明度动画。
 * 页面本身由独立 Activity 承载，进入和返回由系统 Window 转场负责。
 */
@Composable
internal fun SystemBackHandler(
    predictiveBack: Boolean,
    onBack: () -> Unit,
) {
    if (predictiveBack && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        PredictiveBackHandler {
            it.collect { }
            onBack()
        }
    } else {
        BackHandler(onBack = onBack)
    }
}

class FeatureDetailActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val feature = intent.getStringExtra(EXTRA_FEATURE)
            ?.let { name -> FeatureId.entries.firstOrNull { it.name == name } }
            ?: FeatureId.ADB_LABEL
        val store = ThemeStore(this)
        setContent {
            AwooThemeHost(store) { preferences, _ ->
                val scope = rememberCoroutineScope()
                val snackbar = remember { SnackbarHostState() }
                SystemBackHandler(preferences.predictiveBack, ::finish)
                FeatureDetailScreen(
                    feature = feature,
                    onBack = ::finish,
                    snackbar = snackbar,
                    scope = scope,
                    openManager = { openRootManager(this@FeatureDetailActivity) },
                )
            }
        }
    }
}

class ThemeSettingsActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: android.os.Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val store = ThemeStore(this)
        setContent {
            AwooThemeHost(store) { preferences, onChange ->
                SystemBackHandler(preferences.predictiveBack, ::finish)
                ThemeSettingsScreen(
                    preferences = preferences,
                    onChange = onChange,
                    onBack = ::finish,
                )
            }
        }
    }
}

