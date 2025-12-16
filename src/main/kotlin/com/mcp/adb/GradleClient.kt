package com.mcp.adb

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

interface GradleService {
    fun assemble(projectPath: String, buildType: String = "Debug"): String
}

class GradleClient(
    private val timeoutSeconds: Long = 600, // 10 minutes for build
) : GradleService {

    override fun assemble(projectPath: String, buildType: String): String {
        val projectDir = File(projectPath)
        require(projectDir.exists() && projectDir.isDirectory) { "Project directory not found at $projectPath" }

        // Determine wrapper script name
        val wrapperName = if (System.getProperty("os.name").lowercase().contains("win")) "gradlew.bat" else "gradlew"
        val wrapper = File(projectDir, wrapperName)
        
        // If wrapper doesn't exist, we might try global 'gradle' or fail. Let's try wrapper first, then fail for now as standard Android projects have it.
        require(wrapper.exists()) { "Gradle wrapper not found at ${wrapper.absolutePath}" }
        if (!wrapper.canExecute()) {
            wrapper.setExecutable(true)
        }

        val taskName = "assemble$buildType"
        val result = runCommand(
            command = listOf(wrapper.absolutePath, taskName),
            workingDir = projectDir
        )
        return result.stdout + "\n" + result.stderr
    }

    private fun runCommand(command: List<String>, workingDir: File): CommandResult {
        val process = try {
             ProcessBuilder(command)
                .directory(workingDir)
                .start()
        } catch (e: IOException) {
            throw IllegalStateException("Failed to start gradle process: ${e.message}", e)
        }

        val stdoutBuffer = ByteArrayOutputStream()
        val stderrBuffer = ByteArrayOutputStream()

        val stdoutThread = Thread { process.inputStream.use { it.copyTo(stdoutBuffer) } }
        val stderrThread = Thread { process.errorStream.use { it.copyTo(stderrBuffer) } }
        
        stdoutThread.start()
        stderrThread.start()

        val exited = process.waitFor(timeoutSeconds, TimeUnit.SECONDS)
        if (!exited) {
            process.destroyForcibly()
            stdoutThread.join(500)
            stderrThread.join(500)
            throw IllegalStateException("Gradle command timed out after $timeoutSeconds seconds")
        }

        stdoutThread.join()
        stderrThread.join()
        
        val exitCode = process.exitValue()
        val stdout = stdoutBuffer.toString(Charsets.UTF_8)
        val stderr = stderrBuffer.toString(Charsets.UTF_8)

        if (exitCode != 0) {
             throw IllegalStateException("Gradle build failed (exit $exitCode):\n$stdout\n$stderr")
        }

        return CommandResult(stdout, stderr, exitCode)
    }

    private data class CommandResult(val stdout: String, val stderr: String, val exitCode: Int)
}
