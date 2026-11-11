package com.nickwoluff.awoobox.shizuku

import android.content.Context
import android.os.Process
import androidx.annotation.Keep
import com.nickwoluff.awoobox.ComponentState
import com.nickwoluff.awoobox.IHyperGmsService
import com.nickwoluff.awoobox.SystemDumpParser
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.TimeUnit

class PrivilegedService : IHyperGmsService.Stub {

    constructor()

    @Keep
    constructor(@Suppress("UNUSED_PARAMETER") context: Context)

    override fun destroy() {
        System.exit(0)
    }

    override fun getStatus(): String = buildStatus().toString()

    override fun setReceiverEnabled(enabled: Boolean): String {
        val command = if (enabled) {
            listOf("/system/bin/pm", "default-state", "--user", USER_ID, COMPONENT)
        } else {
            listOf("/system/bin/pm", "disable-user", "--user", USER_ID, COMPONENT)
        }
        val result = runCommand(command)
        return buildStatus().apply {
            put("operationExitCode", result.exitCode)
            put("operationOutput", result.output.trim())
            put("operationRequestedEnabled", enabled)
        }.toString()
    }

    private fun buildStatus(): JSONObject {
        val packageDump = runCommand(
            listOf("/system/bin/dumpsys", "package", SECURITY_CENTER_PACKAGE),
            timeoutSeconds = 15,
        )
        val alarmDump = runCommand(
            listOf("/system/bin/dumpsys", "alarm"),
            timeoutSeconds = 20,
        )
        val componentState = SystemDumpParser.parseComponentState(packageDump.output)
        val parsedAlarms = SystemDumpParser.parseCleanerAlarms(alarmDump.output)
        val alarms = JSONArray().apply {
            parsedAlarms.forEach { alarm ->
                put(JSONObject().apply {
                    put("type", alarm.type)
                    put("origWhen", alarm.origWhen)
                    put("window", alarm.window)
                    put("repeatIntervalMillis", alarm.repeatIntervalMillis)
                    put("tag", SystemDumpParser.ALARM_TAG)
                })
            }
        }

        return JSONObject().apply {
            put("checkedAt", System.currentTimeMillis())
            put("executionUid", Process.myUid())
            put("component", COMPONENT)
            put("packageInstalled", packageDump.exitCode == 0 && componentState != ComponentState.UNKNOWN)
            put("componentState", componentState.name)
            put(
                "componentEnabled",
                componentState == ComponentState.DEFAULT || componentState == ComponentState.ENABLED,
            )
            put("taskPresent", alarms.length() > 0)
            put("alarms", alarms)
            put("packageQueryExitCode", packageDump.exitCode)
            put("alarmQueryExitCode", alarmDump.exitCode)
            put(
                "queryError",
                listOfNotNull(
                    packageDump.error ?: packageDump.nonZeroExitMessage("查询手机管家包状态"),
                    alarmDump.error ?: alarmDump.nonZeroExitMessage("查询 AlarmManager"),
                ).joinToString("；"),
            )
        }
    }

    private fun runCommand(command: List<String>, timeoutSeconds: Long = 12): CommandResult {
        return try {
            val process = ProcessBuilder(command)
                .redirectErrorStream(true)
                .start()
            val output = StringBuffer()
            val readError = AtomicReference<Throwable?>(null)
            val reader = Thread({
                runCatching {
                    process.inputStream.bufferedReader().use { output.append(it.readText()) }
                }.onFailure(readError::set)
            }, "hypergms-command-output").apply {
                isDaemon = true
                start()
            }
            val finished = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                process.waitFor(1, TimeUnit.SECONDS)
                reader.join(1_000)
                CommandResult(-1, output.toString(), "命令执行超时：${command.firstOrNull().orEmpty()}")
            } else {
                reader.join(1_000)
                CommandResult(
                    process.exitValue(),
                    output.toString(),
                    readError.get()?.message,
                )
            }
        } catch (error: Throwable) {
            CommandResult(-1, "", error.message ?: error.javaClass.simpleName)
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val output: String,
        val error: String?,
    ) {
        fun nonZeroExitMessage(action: String): String? =
            if (exitCode == 0) null else "${action}失败，退出码 $exitCode"
    }

    companion object {
        private const val USER_ID = "0"
        private const val SECURITY_CENTER_PACKAGE = "com.miui.securitycenter"
        private const val COMPONENT =
            "com.miui.securitycenter/com.miui.securitycenter.receiver.CleanerReceiver"
    }
}

