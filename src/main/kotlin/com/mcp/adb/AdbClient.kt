package com.mcp.adb

import java.io.File
import java.io.IOException
import java.util.Base64
import java.util.concurrent.TimeUnit

interface AdbService {
    fun listDevices(): List<String>
    fun captureScreenshot(deviceId: String? = null): String
    fun installApk(path: String, deviceId: String? = null)
    fun executeShell(command: String, deviceId: String? = null): String
    fun dumpHierarchy(deviceId: String? = null): String
}

class AdbClient(
    private val adbExecutable: String = "adb",
    private val timeoutSeconds: Long = 30,
) : AdbService {

    override fun listDevices(): List<String> {
        val output = runAdbCommand(buildList {
            add("devices")
            add("-l")
        }).stdout

        return output.lineSequence()
            .drop(1) // Skip header
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .mapNotNull { line ->
                val parts = line.split(Regex("\\s+"))
                if (parts.size >= 2 && parts[1] == "device") parts[0] else null
            }
            .toList()
    }

    override fun captureScreenshot(deviceId: String?): String {
        val bytes = runAdbCommandForBytes(buildList {
            addAll(deviceArgs(deviceId))
            addAll(listOf("exec-out", "screencap", "-p"))
        })
        return Base64.getEncoder().encodeToString(bytes)
    }

    override fun installApk(path: String, deviceId: String?) {
        val apk = File(path)
        require(apk.exists()) { "APK not found at $path" }

        runAdbCommand(buildList {
            addAll(deviceArgs(deviceId))
            addAll(listOf("install", "-r", apk.absolutePath))
        })
    }

    override fun executeShell(command: String, deviceId: String?): String {
        val result = runAdbCommand(buildList {
            addAll(deviceArgs(deviceId))
            add("shell")
            add(command)
        })
        return result.stdout.trimEnd()
    }

    override fun dumpHierarchy(deviceId: String?): String {
        val result = runAdbCommand(buildList {
            addAll(deviceArgs(deviceId))
            addAll(listOf("exec-out", "uiautomator", "dump", "/dev/tty"))
        })
        return result.stdout.trimEnd()
    }

    private fun deviceArgs(deviceId: String?): List<String> =
        if (deviceId.isNullOrBlank()) emptyList() else listOf("-s", deviceId)

    private fun runAdbCommand(args: List<String>): CommandResult {
        val process = startProcess(args)
        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw IllegalStateException("adb command timed out after $timeoutSeconds seconds")
        }

        val stdout = process.inputStream.readBytes().toString(Charsets.UTF_8)
        val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)

        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "adb command failed (exit ${process.exitValue()}): ${stderr.ifBlank { stdout }}",
            )
        }

        return CommandResult(stdout, stderr)
    }

    private fun runAdbCommandForBytes(args: List<String>): ByteArray {
        val process = startProcess(args)
        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            throw IllegalStateException("adb command timed out after $timeoutSeconds seconds")
        }

        val stdout = process.inputStream.readBytes()
        val stderr = process.errorStream.readBytes().toString(Charsets.UTF_8)

        if (process.exitValue() != 0) {
            throw IllegalStateException(
                "adb command failed (exit ${process.exitValue()}): $stderr",
            )
        }

        return stdout
    }

    private fun startProcess(args: List<String>) = try {
        ProcessBuilder(buildList {
            add(adbExecutable)
            addAll(args)
        }).start()
    } catch (e: IOException) {
        throw IllegalStateException("adb executable not found on PATH or not executable: $adbExecutable", e)
    }

    private data class CommandResult(val stdout: String, val stderr: String)
}
