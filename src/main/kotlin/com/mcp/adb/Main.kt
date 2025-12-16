package com.mcp.adb

import io.modelcontextprotocol.kotlin.sdk.ServerCapabilities
import io.modelcontextprotocol.kotlin.sdk.server.Server
import io.modelcontextprotocol.kotlin.sdk.server.ServerOptions
import io.modelcontextprotocol.kotlin.sdk.server.StdioServerTransport
import io.modelcontextprotocol.kotlin.sdk.types.CallToolResult
import io.modelcontextprotocol.kotlin.sdk.types.Implementation
import io.modelcontextprotocol.kotlin.sdk.types.TextContent
import io.modelcontextprotocol.kotlin.sdk.types.Tool
import io.modelcontextprotocol.kotlin.sdk.types.ToolSchema
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.io.asSource
import kotlinx.io.asSink
import kotlinx.io.buffered

fun main() = runMcpServer()

fun runMcpServer(adb: AdbService = AdbClient()) {
    val server = Server(
        serverInfo = Implementation(
            name = "adb-mcp-server",
            version = "0.1.0",
        ),
        options = ServerOptions(
            capabilities = ServerCapabilities(
                tools = ServerCapabilities.Tools(listChanged = true),
            ),
        ),
    )

    registerTools(server, adb)

    val transport = StdioServerTransport(
        inputStream = System.`in`.asSource().buffered(),
        outputStream = System.out.asSink().buffered(),
    )

    runBlocking {
        server.connect(transport)
        val done = Job()
        server.onClose { done.complete() }
        done.join()
    }
}

private fun registerTools(server: Server, adb: AdbService) {
    server.addTool(
        name = "list_devices",
        description = "List connected Android devices",
        inputSchema = ToolSchema(
            properties = buildJsonObject { },
            required = emptyList(),
        ),
    ) {
        wrap { CallToolResult(content = listOf(TextContent(adb.listDevices().joinToString("\n")))) }
    }

    server.addTool(
        name = "adb_shell",
        description = "Execute an arbitrary adb shell command",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("command", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Shell command to execute on device"))
                })
                put("deviceId", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional device serial"))
                })
            },
            required = listOf("command"),
        ),
    ) { request ->
        wrap {
            val args = request.arguments ?: JsonObject(emptyMap())
            val command = args["command"]?.jsonPrimitive?.contentOrNull
            val deviceId = args["deviceId"]?.jsonPrimitive?.contentOrNull
            if (command.isNullOrBlank()) {
                return@wrap CallToolResult(content = listOf(TextContent("The 'command' parameter is required.")))
            }
            val output = adb.executeShell(command, deviceId)
            CallToolResult(content = listOf(TextContent(output)))
        }
    }

    server.addTool(
        name = "get_screenshot",
        description = "Capture a screenshot from the device (base64-encoded PNG)",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("deviceId", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional device serial"))
                })
            },
            required = emptyList(),
        ),
    ) { request ->
        wrap {
            val args = request.arguments ?: JsonObject(emptyMap())
            val deviceId = args["deviceId"]?.jsonPrimitive?.contentOrNull
            val pngBase64 = adb.captureScreenshot(deviceId)
            CallToolResult(content = listOf(TextContent(pngBase64)))
        }
    }

    server.addTool(
        name = "install_apk",
        description = "Install an APK on the device",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("path", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Path to APK on the host"))
                })
                put("deviceId", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional device serial"))
                })
            },
            required = listOf("path"),
        ),
    ) { request ->
        wrap {
            val args = request.arguments ?: JsonObject(emptyMap())
            val path = args["path"]?.jsonPrimitive?.content
            val deviceId = args["deviceId"]?.jsonPrimitive?.contentOrNull
            if (path.isNullOrBlank()) {
                return@wrap CallToolResult(content = listOf(TextContent("The 'path' parameter is required.")))
            }
            adb.installApk(path, deviceId)
            CallToolResult(content = listOf(TextContent("ok")))
        }
    }

    server.addTool(
        name = "dump_hierarchy",
        description = "Dump the UI hierarchy XML using uiautomator",
        inputSchema = ToolSchema(
            properties = buildJsonObject {
                put("deviceId", buildJsonObject {
                    put("type", JsonPrimitive("string"))
                    put("description", JsonPrimitive("Optional device serial"))
                })
            },
            required = emptyList(),
        ),
    ) { request ->
        wrap {
            val args = request.arguments ?: JsonObject(emptyMap())
            val deviceId = args["deviceId"]?.jsonPrimitive?.contentOrNull
            val xml = adb.dumpHierarchy(deviceId)
            CallToolResult(content = listOf(TextContent(xml)))
        }
    }
}

private inline fun wrap(block: () -> CallToolResult): CallToolResult =
    try {
        block()
    } catch (e: Exception) {
        CallToolResult(content = listOf(TextContent("Error: ${e.message ?: e::class.simpleName}")))
    }
