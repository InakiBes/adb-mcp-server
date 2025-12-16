package com.mcp.adb

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement

@Serializable
data class McpRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String,
    val params: JsonElement? = null,
)

@Serializable
data class McpResponse(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val result: JsonElement? = null,
    val error: JsonRpcError? = null,
)

@Serializable
data class JsonRpcError(
    val code: Int,
    val message: String,
    val data: JsonElement? = null,
)

@Serializable
data class Tool(
    val name: String,
    val description: String,
    val inputSchema: ToolInputSchema,
)

@Serializable
data class ToolInputSchema(
    val type: String = "object",
    val properties: Map<String, ToolInputProperty> = emptyMap(),
    val required: List<String> = emptyList(),
    val additionalProperties: Boolean = false,
)

@Serializable
data class ToolInputProperty(
    val type: String,
    val description: String? = null,
)

@Serializable
data class Content(
    val type: String,
    val text: String,
)

@Serializable
data class CallToolRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String = "tools/call",
    val params: CallToolParams,
)

@Serializable
data class CallToolParams(
    val name: String,
    val arguments: JsonElement? = null,
)

@Serializable
data class ListToolsRequest(
    val jsonrpc: String = "2.0",
    val id: JsonElement? = null,
    val method: String = "tools/list",
    val params: ListToolsParams = ListToolsParams(),
)

@Serializable
data class ListToolsParams(
    val cursor: String? = null,
)
