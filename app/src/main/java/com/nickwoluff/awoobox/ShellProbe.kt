package com.nickwoluff.awoobox

import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

data class ProbeResult(
    val enforced: Boolean,
    val adbdLabel: String,
    val shellLabel: String,
    val tcpPort: String,
    val rootState: RootState,
    val detail: String? = null,
)

enum class RootState {
    GRANTED,
    NOT_GRANTED,
    UNAVAILABLE,
}

data class RepairResult(
    val success: Boolean,
    val log: String,
)

object ShellProbe {
    private const val ADBD = "/dev/__properties__/u:object_r:adbd_config_prop:s0"
    private const val SHELL = "/dev/__properties__/u:object_r:shell_prop:s0"

    private data class CommandOutput(val text: String, val spawnError: Boolean)

    private fun run(command: Array<String>, timeoutSeconds: Long = 8): CommandOutput {
        return try {
            val process = ProcessBuilder(*command).redirectErrorStream(true).start()
            val output = process.inputStream.use { input ->
                ByteArrayOutputStream().also { buffer -> input.copyTo(buffer) }.toString("UTF-8")
            }
            if (!process.waitFor(timeoutSeconds, TimeUnit.SECONDS)) {
                process.destroyForcibly()
                CommandOutput("$output\nTIMEOUT".trim(), false)
            } else {
                CommandOutput(output.trim(), false)
            }
        } catch (e: Exception) {
            CommandOutput("ERROR:${e.javaClass.simpleName}: ${e.message ?: "无法启动 su"}", true)
        }
    }

    fun checkRoot(): Pair<RootState, String> {
        val result = run(arrayOf("su", "-c", "id -u"), timeoutSeconds = 8)
        if (result.spawnError) {
            return RootState.UNAVAILABLE to "无法启动 su。请确认设备已通过 KernelSU 或 Magisk 获取 Root，并在管理器中允许嗷呜工具箱。"
        }
        val uid = result.text.lineSequence().map { it.trim() }.lastOrNull { it.isNotEmpty() }.orEmpty()
        if (uid == "0") return RootState.GRANTED to ""
        if (uid.contains("permission", ignoreCase = true) || uid.contains("denied", ignoreCase = true)) {
            return RootState.NOT_GRANTED to "Root 权限未授予。"
        }
        return RootState.NOT_GRANTED to "Root 权限未授予（su 返回：${uid.ifBlank { "空" }}）。"
    }

    fun probe(): ProbeResult {
        val (rootState, rootDetail) = checkRoot()
        if (rootState != RootState.GRANTED) {
            return ProbeResult(
                enforced = true,
                adbdLabel = "需要授权后读取",
                shellLabel = "需要授权后读取",
                tcpPort = "",
                rootState = rootState,
                detail = rootDetail,
            )
        }

        val script = "echo ENFORCE:$(getenforce); echo ADBD:$(ls -Z $ADBD 2>/dev/null); echo SHELL:$(ls -Z $SHELL 2>/dev/null); echo TCP:$(getprop service.adb.tcp.port)"
        val output = run(arrayOf("su", "-c", script)).text
        fun line(prefix: String) = output.lineSequence()
            .firstOrNull { it.startsWith("$prefix:") }
            ?.substringAfter("$prefix:")
            ?.trim()

        val enforce = line("ENFORCE").orEmpty()
        val adbd = line("ADBD").orEmpty()
        val shell = line("SHELL").orEmpty()
        val tcp = line("TCP").orEmpty()
        return ProbeResult(
            enforced = !enforce.equals("Permissive", ignoreCase = true),
            adbdLabel = adbd.contextLabel(),
            shellLabel = shell.contextLabel(),
            tcpPort = tcp,
            rootState = RootState.GRANTED,
            detail = when {
                enforce.isBlank() -> "无法确认 SELinux 状态。"
                adbd.isBlank() -> "无法读取 adbd 属性文件。"
                else -> null
            },
        )
    }

    fun repairLabels(fixShellProp: Boolean): RepairResult {
        val (rootState, detail) = checkRoot()
        if (rootState != RootState.GRANTED) {
            return RepairResult(
                false,
                "未执行修复。$detail\n请打开 KernelSU / Magisk 管理器，在超级用户列表中允许“嗷呜工具箱”，再返回重试。",
            )
        }

        val optionalShell = if (fixShellProp) {
            "if chcon u:object_r:shell_prop:s0 $SHELL; then echo SHELL:$(ls -Z $SHELL); else echo SHELL_ERROR:permission denied; fi"
        } else {
            "true"
        }
        val script = "if chcon u:object_r:adbd_config_prop:s0 $ADBD; then echo ADBD:$(ls -Z $ADBD); else echo ADBD_ERROR:permission denied; fi; $optionalShell"
        val output = run(arrayOf("su", "-c", script), timeoutSeconds = 20)
        if (output.spawnError) {
            return RepairResult(false, "无法启动 su。\n${output.text}")
        }
        val success = output.text.contains("ADBD:") && output.text.contains("adbd_config_prop")
        return RepairResult(success, output.text.ifBlank { "命令没有返回输出。" })
    }

    private fun String.contextLabel(): String {
        if (isBlank()) return "未知"
        val tokens = trim().split(Regex("\\s+"))
        return tokens.firstOrNull { it.startsWith("u:object_r:") } ?: tokens.lastOrNull() ?: "未知"
    }
}
