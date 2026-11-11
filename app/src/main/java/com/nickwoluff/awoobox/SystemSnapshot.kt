package com.nickwoluff.awoobox

import org.json.JSONObject
import java.time.Duration
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.time.format.FormatStyle
import java.util.Locale

data class SystemSnapshot(
    val checkedAt: Long = 0L,
    val packageInstalled: Boolean = false,
    val componentState: ComponentState = ComponentState.UNKNOWN,
    val componentEnabled: Boolean = false,
    val taskPresent: Boolean = false,
    val alarms: List<AlarmEntry> = emptyList(),
    val queryError: String = "",
    val operationExitCode: Int? = null,
    val operationOutput: String = "",
    val executionUid: Int? = null,
) {
    val effectiveProtection: ProtectionState
        get() = when {
            !packageInstalled -> ProtectionState.UNSUPPORTED
            componentState == ComponentState.UNKNOWN -> ProtectionState.UNKNOWN
            !componentEnabled -> ProtectionState.PROTECTED
            taskPresent -> ProtectionState.EXPOSED
            else -> ProtectionState.NO_TASK
        }

    companion object {
        fun fromJson(raw: String): SystemSnapshot {
            val json = JSONObject(raw)
            val alarmJson = json.optJSONArray("alarms")
            val alarms = buildList {
                if (alarmJson != null) {
                    for (index in 0 until alarmJson.length()) {
                        val item = alarmJson.getJSONObject(index)
                        add(
                            AlarmEntry(
                                type = item.optString("type"),
                                origWhen = item.optString("origWhen"),
                                window = item.optString("window", "0"),
                                repeatIntervalMillis = item.optLong("repeatIntervalMillis"),
                            ),
                        )
                    }
                }
            }
            return SystemSnapshot(
                checkedAt = json.optLong("checkedAt"),
                packageInstalled = json.optBoolean("packageInstalled"),
                componentState = ComponentState.entries.firstOrNull {
                    it.name == json.optString("componentState")
                } ?: ComponentState.UNKNOWN,
                componentEnabled = json.optBoolean("componentEnabled"),
                taskPresent = json.optBoolean("taskPresent"),
                alarms = alarms,
                queryError = json.optString("queryError"),
                operationExitCode = if (json.has("operationExitCode")) json.optInt("operationExitCode") else null,
                operationOutput = json.optString("operationOutput"),
                executionUid = if (json.has("executionUid")) json.optInt("executionUid") else null,
            )
        }
    }

    fun operationFailureMessage(): String? {
        if (operationExitCode == null || operationExitCode == 0) return null
        if (operationOutput.contains("Shell cannot change component state", ignoreCase = true)) {
            return "停用失败：当前 Shizuku 以 ADB/Shell 身份运行，HyperOS 禁止 Shell 修改系统手机管家的组件状态。请将 Shizuku 切换为 Root 模式（或使用 Sui）；仅 ADB 模式无法完成此操作。"
        }
        val firstLine = operationOutput.lineSequence()
            .map(String::trim)
            .firstOrNull { it.isNotBlank() }
        return "操作失败：${firstLine ?: "系统未返回原因（退出码 $operationExitCode）"}"
    }

    fun executionModeLabel(): String = when (executionUid) {
        0 -> "Root"
        2000 -> "ADB/Shell"
        null -> "未知"
        else -> "UID $executionUid"
    }
}

enum class ComponentState {
    DEFAULT,
    ENABLED,
    DISABLED,
    DISABLED_USER,
    UNKNOWN,
}

enum class ProtectionState {
    PROTECTED,
    EXPOSED,
    NO_TASK,
    UNSUPPORTED,
    UNKNOWN,
}

data class AlarmEntry(
    val type: String,
    val origWhen: String,
    val window: String,
    val repeatIntervalMillis: Long,
) {
    private val parsedTime: LocalDateTime?
        get() = try {
            LocalDateTime.parse(origWhen, RAW_TIME_FORMAT)
        } catch (_: DateTimeParseException) {
            null
        }

    fun displayStart(): String = parsedTime?.format(DISPLAY_TIME_FORMAT) ?: origWhen.ifBlank { "未知" }

    fun displayEnd(): String {
        val start = parsedTime ?: return "由系统窗口调度"
        val duration = parseWindow(window) ?: return "由系统窗口调度"
        if (duration.isZero) return displayStart()
        return start.plus(duration).format(DISPLAY_TIME_FORMAT)
    }

    fun displayWindow(): String = when {
        window == "0" || window.isBlank() -> "精确时间"
        else -> "窗口 ${formatWindow(window)}"
    }

    fun displayRepeat(): String = when (repeatIntervalMillis) {
        86_400_000L -> "每天重复"
        0L -> "单次任务"
        else -> "每 ${Duration.ofMillis(repeatIntervalMillis).toHours()} 小时重复"
    }

    private fun parseWindow(value: String): Duration? {
        val match = WINDOW_PATTERN.matchEntire(value) ?: return null
        val sign = if (match.groupValues[1] == "-") -1 else 1
        val days = match.groupValues[2].toLongOrNull() ?: 0L
        val hours = match.groupValues[3].toLongOrNull() ?: 0L
        val minutes = match.groupValues[4].toLongOrNull() ?: 0L
        val seconds = match.groupValues[5].toLongOrNull() ?: 0L
        val millis = match.groupValues[6].toLongOrNull() ?: 0L
        return Duration.ofDays(days)
            .plusHours(hours)
            .plusMinutes(minutes)
            .plusSeconds(seconds)
            .plusMillis(millis)
            .multipliedBy(sign.toLong())
    }

    private fun formatWindow(value: String): String {
        val duration = parseWindow(value) ?: return value
        val totalSeconds = duration.seconds
        val days = totalSeconds / 86_400
        val hours = totalSeconds % 86_400 / 3_600
        val minutes = totalSeconds % 3_600 / 60
        val seconds = totalSeconds % 60
        val parts = buildList {
            if (days != 0L) add("$days 天")
            if (hours != 0L) add("$hours 小时")
            if (minutes != 0L) add("$minutes 分钟")
            if (seconds != 0L) add("$seconds 秒")
        }
        return parts.joinToString().ifBlank { "0" }
    }

    companion object {
        private val RAW_TIME_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
        private val DISPLAY_TIME_FORMAT = DateTimeFormatter.ofLocalizedDateTime(FormatStyle.MEDIUM)
            .withLocale(Locale.SIMPLIFIED_CHINESE)
        private val WINDOW_PATTERN =
            Regex("([+-]?)(?:(\\d+)d)?(?:(\\d+)h)?(?:(\\d+)m)?(?:(\\d+)s)?(?:(\\d+)ms)?")
    }
}

