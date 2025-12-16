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
    private val gradle: GradleService = GradleClient(),
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
            
            "get_screenshot" -> {
                val args = json.decodeFromJsonElement<DeviceScopedArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.captureScreenshot(args.deviceId))
            }

            "install_apk" -> {
                val args = json.decodeFromJsonElement<InstallApkArgs>(call.arguments ?: emptyObject)
                adb.installApk(args.path, args.deviceId)
                JsonPrimitive("ok")
            }
            
            "uninstall_package" -> {
                val args = json.decodeFromJsonElement<UninstallPackageArgs>(call.arguments ?: emptyObject)
                adb.uninstallApp(args.packageName, args.keepData, args.deviceId)
                JsonPrimitive("ok")
            }

            "start_activity" -> {
                val args = json.decodeFromJsonElement<StartActivityArgs>(call.arguments ?: emptyObject)
                adb.startActivity(args.packageName, args.activityName, args.action, args.dataUri, args.deviceId)
                JsonPrimitive("ok")
            }
            
            "deep_link" -> {
                 val args = json.decodeFromJsonElement<DeepLinkArgs>(call.arguments ?: emptyObject)
                 // Map deep link to start activity with data uri
                 // Assuming deep link implies ACTION_VIEW usually, but intent scheme works too.
                 val action = "android.intent.action.VIEW"
                 adb.startActivity(args.packageName, args.activityName, action, args.uri, args.deviceId)
                 JsonPrimitive("ok")
            }

            "force_stop" -> {
                val args = json.decodeFromJsonElement<PackageScopedArgs>(call.arguments ?: emptyObject)
                adb.stopApp(args.packageName, args.deviceId)
                JsonPrimitive("ok")
            }

            "clear_app_data" -> {
                val args = json.decodeFromJsonElement<PackageScopedArgs>(call.arguments ?: emptyObject)
                adb.clearAppData(args.packageName, args.deviceId)
                JsonPrimitive("ok")
            }
            
            "current_activity" -> {
                val args = json.decodeFromJsonElement<DeviceScopedArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.getCurrentActivity(args.deviceId))
            }

            "dump_hierarchy" -> {
                val args = json.decodeFromJsonElement<DeviceScopedArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(adb.dumpHierarchy(args.deviceId))
            }
            
            "gradle_assemble" -> {
                val args = json.decodeFromJsonElement<GradleAssembleArgs>(call.arguments ?: emptyObject)
                JsonPrimitive(gradle.assemble(args.projectPath, args.buildType ?: "Debug"))
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

    private data class DeviceScopedArgs(val deviceId: String? = null)
    private data class InstallApkArgs(val path: String, val deviceId: String? = null)
    private data class UninstallPackageArgs(val packageName: String, val keepData: Boolean = false, val deviceId: String? = null)
    private data class StartActivityArgs(val packageName: String, val activityName: String? = null, val action: String? = null, val dataUri: String? = null, val deviceId: String? = null)
    private data class DeepLinkArgs(val packageName: String, val uri: String, val activityName: String? = null, val deviceId: String? = null)
    private data class PackageScopedArgs(val packageName: String, val deviceId: String? = null)
    private data class GradleAssembleArgs(val projectPath: String, val buildType: String? = "Debug")

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
            name = "get_screenshot",
            description = "Capture a screenshot from the device (base64-encoded PNG)",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
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
                    "path" to ToolInputProperty(type = "string", description = "Path to APK on the host"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("path"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "uninstall_package",
            description = "Uninstall an application",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "packageName" to ToolInputProperty(type = "string", description = "Package name to uninstall"),
                    "keepData" to ToolInputProperty(type = "boolean", description = "Keep data and cache directories"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("packageName"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "start_activity",
            description = "Launch an application or a specific Activity",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "packageName" to ToolInputProperty(type = "string", description = "Package name"),
                    "activityName" to ToolInputProperty(type = "string", description = "Optional activity class name"),
                    "action" to ToolInputProperty(type = "string", description = "Optional intent action"),
                    "dataUri" to ToolInputProperty(type = "string", description = "Optional intent data URI"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("packageName"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "deep_link",
            description = "Open application using a deep link URI",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "packageName" to ToolInputProperty(type = "string", description = "Package name"),
                    "uri" to ToolInputProperty(type = "string", description = "Deep link URI"),
                    "activityName" to ToolInputProperty(type = "string", description = "Optional specific activity to handle the link"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("packageName", "uri"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "force_stop",
            description = "Force stop a running application",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "packageName" to ToolInputProperty(type = "string", description = "Package name"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("packageName"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "clear_app_data",
            description = "Clear application data",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "packageName" to ToolInputProperty(type = "string", description = "Package name"),
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = listOf("packageName"),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "current_activity",
            description = "Retrieve the current foreground activity",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = emptyList(),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "dump_hierarchy",
            description = "Dump the UI hierarchy XML using uiautomator",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "deviceId" to ToolInputProperty(type = "string", description = "Optional device serial"),
                ),
                required = emptyList(),
                additionalProperties = false,
            ),
        ),
        Tool(
            name = "gradle_assemble",
            description = "Compile Android project",
            inputSchema = ToolInputSchema(
                properties = mapOf(
                    "projectPath" to ToolInputProperty(type = "string", description = "Absolute path to project root"),
                    "buildType" to ToolInputProperty(type = "string", description = "Build type (e.g. Debug, Release). Defaults to Debug."),
                ),
                required = listOf("projectPath"),
                additionalProperties = false,
            ),
        ),
    )
}
