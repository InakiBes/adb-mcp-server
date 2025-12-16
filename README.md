**Project:** `adb-mcp-server`
**Runtime:** JVM (Kotlin)
**Integration:** Stdio (JSON-RPC 2.0)

**Overview:**
This server implements the MCP protocol to act as an intermediate bridge between Large Language Models (LLMs) and physically connected or emulated Android devices. It abstracts complex ADB CLI commands into AI-callable tools, enabling automation, testing, and debugging flows without direct human intervention.

**General Capabilities:**

*   List connected serials and choose command targets.
*   Run arbitrary commands via `adb shell` for inspection, debugging, and automation.
*   Capture screens (`screencap`) and dump the view tree (`uiautomator dump`) for semantic analysis.
*   Install APKs and quickly re-instrument builds on the target device.

**Exposed MCP Tools:**

| Tool | Parameters | Returns | Use Case |
| :--- | :--- | :--- | :--- |
| `list_devices` | — | List of connected serials (one per line) | Discover which device to target or validate connection. |
| `adb_shell` | `command` (req.), `deviceId` (opt.) | `stdout` of the command on the device | Execute `pm list packages`, `am start`, `input tap`, etc. |
| `get_screenshot` | `deviceId` (opt.) | Base64 PNG (screencap) | Extract visual context for vision-enabled agents. |
| `install_apk` | `path` (req.), `deviceId` (opt.) | `"ok"` after installation with `adb install -r` | Hot-deploy a local build. |
| `dump_hierarchy` | `deviceId` (opt.) | XML from `uiautomator dump /dev/tty` | Read UI structure and plan interactions. |

`deviceId` is always optional; if omitted, ADB uses the default device. The `screenshot` comes in base64 ready to be decoded by the MCP client.

**Possible flows with these tools:**

*   **Execute manual smoke tests**: install an APK, launch the main activity, inspect logs/state with `adb_shell`.
*   **Debug screens**: capture screenshot + XML tree and ask the AI to describe elements or suggest taps.
*   **Maintenance operations**: list connected devices in CI, clear app data, force package restarts, etc.

### `tools/call` Example Calls

All calls use `method: "tools/call"` with `params.name` as the tool and `params.arguments` as the payload.

`list_devices`

```json
{
  "method": "tools/call",
  "params": { "name": "list_devices", "arguments": {} }
}
```
Typical result (`content[0].text`):
```
emulator-5554
0123456789ABCDEF
```

`adb_shell` (with optional `deviceId`)

```json
{
  "method": "tools/call",
  "params": {
    "name": "adb_shell",
    "arguments": {
      "command": "getprop ro.build.version.release",
      "deviceId": "emulator-5554"
    }
  }
}
```
Result (`content[0].text`): e.g. `14`.

`get_screenshot`

```json
{
  "method": "tools/call",
  "params": { "name": "get_screenshot", "arguments": { "deviceId": "emulator-5554" } }
}
```
Result (`content[0].text`): Base64 PNG string ready for decoding.

`install_apk`

```json
{
  "method": "tools/call",
  "params": {
    "name": "install_apk",
    "arguments": { "path": "/tmp/app-debug.apk", "deviceId": "emulator-5554" }
  }
}
```
Result (`content[0].text`): `ok` if the installation was successful.

`dump_hierarchy`

```json
{
  "method": "tools/call",
  "params": { "name": "dump_hierarchy", "arguments": { "deviceId": "emulator-5554" } }
}
```
Result (`content[0].text`): Complete XML of the view hierarchy (`uiautomator dump /dev/tty`).

### MCP Initialization Payload (Code)

This is the **critical** description that the agent must return in the response to the `initialize` method. It is what the AI (Cursor/Claude) "reads" to understand what this server is.

```kotlin
// Inside your response to the 'initialize' method
val serverInfo = ServerInfo(
    name = "adb-mcp-server",
    version = "0.1.0"
)

val capabilities = Capabilities(
    tools = ToolsCapability(listChanged = true),
    resources = null, // Optional if you decide to expose logs as resources
    prompts = null    // Optional
)
```

**Instruction for the final JSON:**

```json
{
  "name": "adb-mcp-server",
  "version": "0.1.0",
  "description": "Provides control over Android devices via ADB. Lists devices, runs shell commands, installs APKs, takes screenshots (base64 PNG), and dumps UI hierarchy XML."
}
```
