package com.mcp.adb

import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonPrimitive
import java.io.BufferedReader
import java.io.InputStreamReader

class McpServer(
    private val adb: AdbService = AdbClient(),
    private val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    },
) {

    fun run() {
        val reader = BufferedReader(InputStreamReader(System.`in`))
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) continue

            val response: McpResponse = try {
                val request = json.decodeFromString<McpRequest>(line)
                handleRequest(request)
            } catch (e: SerializationException) {
                errorResponse(id = null, code = -32700, message = "Parse error: ${e.message}")
            } catch (e: Exception) {
                errorResponse(id = null, code = -32603, message = "Internal error: ${e.message}")
            }

            println(json.encodeToString(response))
            System.out.flush()
        }
    }

    private fun handleRequest(request: McpRequest): McpResponse = try {
        when (request.method) {
            "initialize" -> McpResponse(
                id = request.id,
                result = buildJsonObject {
                    put("capabilities", buildJsonObject {
                        put("tools", JsonPrimitive(true))
                    })
                },
            )

            "tools/list" -> McpResponse(
                id = request.id,
                result = buildJsonObject {
                    put("tools", JsonArray(availableTools.map { json.encodeToJsonElement(it) }))
                },
            )

            "tools/call" -> handleToolCall(request)

            else -> errorResponse(
                id = request.id,
                code = -32601,
                message = "Method not found: ${request.method}",
            )
        }
    } catch (e: SerializationException) {
        errorResponse(id = request.id, code = -32602, message = "Invalid params: ${e.message}")
    } catch (e: Exception) {
        errorResponse(id = request.id, code = -32603, message = "Internal error: ${e.message}")
    }

    private fun handleToolCall(request: McpRequest): McpResponse {
        val params = request.params ?: return errorResponse(
            id = request.id,
            code = -32602,
            message = "Missing params for tools/call",
        )

        val call = json.decodeFromJsonElement<CallToolParams>(params)
        val resultPayload: JsonElement = when (call.name) {
            "list_devices" -> json.encodeToJsonElement(adb.listDevices())
            "adb_shell" -> {
                val args = json.decodeFromJsonElement<AdbShellArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.executeShell(args.command, args.deviceId))
            }

            "get_screenshot" -> {
                val args = json.decodeFromJsonElement<DeviceScopedArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.captureScreenshot(args.deviceId))
            }

            "install_apk" -> {
                val args = json.decodeFromJsonElement<InstallApkArgs>(call.arguments ?: emptyObject)
                adb.installApk(args.path, args.deviceId)
                JsonPrimitive("ok")
            }

            "dump_hierarchy" -> {
                val args = json.decodeFromJsonElement<DeviceScopedArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.dumpHierarchy(args.deviceId))
            }

            else -> return errorResponse(
                id = request.id,
                code = -32601,
                message = "Tool not found: ${call.name}",
            )
        }

        val textValue = if (resultPayload is JsonPrimitive && resultPayload.isString) {
            resultPayload.content
        } else {
            json.encodeToString(JsonElement.serializer(), resultPayload)
        }

        return McpResponse(
            id = request.id,
            result = buildJsonObject {
                put("content", buildJsonArray {
                    add(buildJsonObject {
                        put("type", JsonPrimitive("text"))
                        put("text", JsonPrimitive(textValue))
                    })
                })
            },
        )
    }

    private fun errorResponse(id: JsonElement?, code: Int, message: String) = McpResponse(
        id = id,
        error = JsonRpcError(code = code, message = message),
    )

    private data class AdbShellArgs(val command: String, val deviceId: String? = null)
    private data class DeviceScopedArgs(val deviceId: String? = null)
    private data class InstallApkArgs(val path: String, val deviceId: String? = null)

    private val emptyObject: JsonObject = buildJsonObject { }

    private val availableTools: List<Tool> = listOf(
        Tool(
            name = "list_devices",
            description = "List connected Android devices",
            inputSchema = ToolInputSchema(
                properties = emptyMap(),
                required = emptyList(),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "adb_shell",
            description = "Execute an arbitrary adb shell command",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "command" to ToolInputProperty(
                        type = "string",
                        description = "Shell command to execute on device",
                    ),
                    "deviceId" to ToolInputProperty(
                        type = "string",
                        description = "Optional device serial",
                    ),
                ),
                required = listOf("command"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "get_screenshot",
            description = "Capture a screenshot from the device (base64-encoded PNG)",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "deviceId" to ToolInputProperty(
                        type = "string",
                        description = "Optional device serial",
                    ),
                ),
                required = emptyList(),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "install_apk",
            description = "Install an APK on the device",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "path" to ToolInputProperty(
                        type = "string",
                        description = "Path to APK on the host",
                    ),
                    "deviceId" to ToolInputProperty(
                        type = "string",
                        description = "Optional device serial",
                    ),
                ),
                required = listOf("path"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "dump_hierarchy",
            description = "Dump the UI hierarchy XML using uiautomator",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "deviceId" to ToolInputProperty(
                        type = "string",
                        description = "Optional device serial",
                    ),
                ),
                required = emptyList(),
                additionalProperties = false,
            ),
        ),
    )
}
