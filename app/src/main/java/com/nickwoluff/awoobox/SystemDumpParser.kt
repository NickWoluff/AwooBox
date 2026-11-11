package com.nickwoluff.awoobox

internal data class ParsedAlarm(
    val type: String,
    val origWhen: String,
    val window: String,
    val repeatIntervalMillis: Long,
)

internal object SystemDumpParser {
    const val ALARM_TAG = "*alarm*:com.miui.securitycenter/.receiver.CleanerReceiver"

    private const val COMPONENT_CLASS = "com.miui.securitycenter.receiver.CleanerReceiver"
    private const val COMPONENT_SHORT_CLASS = ".receiver.CleanerReceiver"

    fun parseComponentState(dump: String): ComponentState {
        val lines = dump.lineSequence().toList()
        val userStart = lines.indexOfFirst {
            USER_ZERO_PATTERN.matches(it)
        }
        if (userStart < 0) return ComponentState.UNKNOWN

        val userIndent = lines[userStart].indentWidth()
        val userEnd = (userStart + 1 until lines.size).firstOrNull { index ->
            val line = lines[index]
            line.isNotBlank() && line.indentWidth() <= userIndent
        } ?: lines.size
        val userLines = lines.subList(userStart, userEnd)
        val disabledComponents = parseComponentList(userLines, "disabledComponents:")
        val enabledComponents = parseComponentList(userLines, "enabledComponents:")

        return when {
            disabledComponents.any(::isTargetComponent) -> ComponentState.DISABLED_USER
            enabledComponents.any(::isTargetComponent) -> ComponentState.ENABLED
            else -> ComponentState.DEFAULT
        }
    }

    fun parseCleanerAlarms(dump: String): List<ParsedAlarm> {
        val lines = dump.lineSequence().toList()
        return buildList {
            lines.forEachIndexed { index, line ->
                if (!line.contains(ALARM_TAG)) return@forEachIndexed
                val header = lines.getOrNull(index - 1)?.trim().orEmpty()
                val details = lines.getOrNull(index + 1)?.trim().orEmpty()
                if (!ALARM_HEADER_PATTERN.matches(header)) return@forEachIndexed

                val type = TYPE_PATTERN.find(details)?.groupValues?.get(1) ?: return@forEachIndexed
                val origWhen = ORIG_WHEN_PATTERN.find(details)?.groupValues?.get(1) ?: return@forEachIndexed
                val repeatInterval = REPEAT_PATTERN.find(details)?.groupValues?.get(1)
                    ?.toLongOrNull() ?: return@forEachIndexed
                val window = WINDOW_PATTERN.find(details)?.groupValues?.get(1).orEmpty().ifBlank { "0" }

                add(
                    ParsedAlarm(
                        type = type,
                        origWhen = origWhen,
                        window = window,
                        repeatIntervalMillis = repeatInterval,
                    ),
                )
            }
        }
    }

    private fun parseComponentList(lines: List<String>, heading: String): List<String> {
        val headingIndex = lines.indexOfFirst { it.trim() == heading }
        if (headingIndex < 0) return emptyList()
        val headingIndent = lines[headingIndex].indentWidth()
        return lines.drop(headingIndex + 1)
            .takeWhile { it.isBlank() || it.indentWidth() > headingIndent }
            .map(String::trim)
            .filter(String::isNotBlank)
    }

    private fun isTargetComponent(value: String): Boolean =
        value == COMPONENT_CLASS || value == COMPONENT_SHORT_CLASS

    private fun String.indentWidth(): Int = indexOfFirst { !it.isWhitespace() }
        .let { if (it < 0) length else it }

    private val USER_ZERO_PATTERN = Regex("^\\s*User 0:.*\\binstalled=.*$")
    private val ALARM_HEADER_PATTERN = Regex("(?:RTC|RTC_WAKEUP) #\\d+:.*")
    private val TYPE_PATTERN = Regex("\\btype=(RTC(?:_WAKEUP)?)\\b")
    private val ORIG_WHEN_PATTERN =
        Regex("\\borigWhen=(\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}\\.\\d{3})")
    private val WINDOW_PATTERN = Regex("\\bwindow=([^\\s]+)")
    private val REPEAT_PATTERN = Regex("\\brepeatInterval=(\\d+)")
}

