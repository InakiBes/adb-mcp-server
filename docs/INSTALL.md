# 🚀 ADB MCP Server - Installation Guide

Setup guide for integrating `adb-mcp-server` with major AI clients.

## 0. Prerequisites

1.  **Java Runtime (JRE/JDK 17+):** Verify with `java -version`.
2.  **ADB Installed:** Verify with `adb --version`.
3.  **Build the Project:**
    ```bash
    ./gradlew installDist
    # Generates the startup script at:
    # ./build/install/adb-mcp-server/bin/adb-mcp-server
    ```

-----

## 1. Claude Code / Claude Desktop

Claude uses a global JSON configuration file.

**File Location:**

  * **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
  * **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

**Configuration:**

```json
{
  "mcpServers": {
    "android-adb": {
      "command": "/ABSOLUTE/PATH/TO/YOUR/PROJECT/build/install/adb-mcp-server/bin/adb-mcp-server",
      "args": [],
      "env": {
        "PATH": "/usr/bin:/usr/local/bin:/opt/homebrew/bin" 
      }
    }
  }
}
```

> **Note:** The `env` variable is critical. MCP processes often do not inherit your shell's PATH (zsh/bash). If this is not defined, the server may fail when attempting to execute `adb`.

-----

## 2. Cursor (Agent Mode)

Cursor supports adding MCP servers directly through its settings interface.

1.  Open **Cursor Settings** (`Cmd + ,` or `Ctrl + ,`).
2.  Navigate to **General > MCP**.
3.  Click on **"Add New MCP Server"**.
4.  Fill in the fields:

| Field | Value |
| :--- | :--- |
| **Name** | `android-adb` |
| **Type** | `command` (stdio) |
| **Command** | `/ABSOLUTE/PATH/.../build/install/adb-mcp-server/bin/adb-mcp-server` |

5.  The indicator should turn green (Connected). If it fails, check the "Output" > "MCP" console.

-----

## 3. GitHub Copilot (VS Code)

Currently, GitHub Copilot in VS Code accesses MCP tools through extensions or experimental configurations. The most reliable method currently is via the **"Model Context Protocol"** extension in VS Code, which injects the context into Copilot.

**File Location:** `.vscode/settings.json` (Workspace) or Global Settings.

```json
{
    "mcp.servers": {
        "android-adb": {
            "command": "/ABSOLUTE/PATH/.../build/install/adb-mcp-server/bin/adb-mcp-server",
            "args": []
        }
    }
}
```

*Restart VS Code after applying changes.*

-----

## 4. Gemini CLI / Custom Agents

If you are using a Gemini CLI wrapper that supports MCP (or a custom Python/Node script using the Gemini SDK), servers are typically passed as arguments upon agent startup.

**Example Invocation (Flag style):**

```bash
gemini-cli chat \
  --mcp-server "android-adb=/ABSOLUTE/PATH/.../bin/adb-mcp-server" \
  --system-prompt "You have access to Android ADB tools. Use them to help me debug."
```

**If using a configuration file (`mcp_config.json`):**

```json
{
  "mcpServers": {
    "adb": {
      "command": "java",
      "args": ["-jar", "/ABSOLUTE/PATH/build/libs/adb-mcp-server-all.jar"]
    }
  }
}
```

-----

## 5. Codex CLI / Codex Plugin (IDE)

Codex utilizes a centralized configuration in TOML format, shared between the CLI and the IDE plugin.

### Method 1: Via CLI

If you have the Codex CLI installed, you can add the server by running:

```bash
codex mcp add android-adb \
  --env PATH="/usr/bin:/usr/local/bin:/opt/homebrew/bin" \
  -- /ABSOLUTE/PATH/TO/YOUR/PROJECT/build/install/adb-mcp-server/bin/adb-mcp-server
```

> **Note:** The `env` variable is important to ensure the server can locate the `adb` binary.

### Method 2: Manual Configuration (`config.toml`)

Edit the global configuration file:

**File:** `~/.codex/config.toml` (create this file if it does not exist).

Add the following configuration:

```toml
[mcp_servers.android-adb]
command = "/ABSOLUTE/PATH/TO/YOUR/PROJECT/build/install/adb-mcp-server/bin/adb-mcp-server"
args = []

[mcp_servers.android-adb.env]
PATH = "/usr/bin:/usr/local/bin:/opt/homebrew/bin"
```

-----

## 🛠 Common Troubleshooting

**Error: "adb command not found"**
The MCP server starts but fails when calling `adb`.

  * **Solution:** Inside your Kotlin code (`AdbClient`), use the absolute path to the ADB binary (e.g. `/Users/user/Library/Android/sdk/platform-tools/adb`) OR ensure you pass the correct PATH in the client's `env` configuration (see the Claude section).

**Error: "Connection refused / EACCES"**
Missing execution permissions.

  * **Solution:** Run `chmod +x ./build/install/adb-mcp-server/bin/adb-mcp-server`
