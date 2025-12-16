# 🚀 ADB MCP Server - Installation Guide

Guía de configuración para integrar `adb-mcp-server` con los principales clientes de IA.

## 0\. Prerrequisitos

1.  **Java Runtime (JRE/JDK 17+):** Verificar con `java -version`.
2.  **ADB Installed:** Verificar con `adb --version`.
3.  **Build del Proyecto:**
    ```bash
    ./gradlew installDist
    # Genera el script de arranque en:
    # ./build/install/adb-mcp-server/bin/adb-mcp-server
    ```

-----

## 1\. Claude Code / Claude Desktop

Claude utiliza un archivo de configuración JSON global.

**Archivo:**

  * **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
  * **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

**Configuración:**

```json
{
  "mcpServers": {
    "android-adb": {
      "command": "/RUTE/ABSOLUTA/A/TU/PROYECTO/build/install/adb-mcp-server/bin/adb-mcp-server",
      "args": [],
      "env": {
        "PATH": "/usr/bin:/usr/local/bin:/opt/homebrew/bin" 
      }
    }
  }
}
```

> **Nota:** La variable `env` es crítica. Los procesos MCP a veces no heredan el PATH de tu shell (zsh/bash), por lo que si no lo defines, el servidor podría fallar al intentar ejecutar `adb`.

-----

## 2\. Cursor (Agent Mode)

Cursor permite añadir servidores MCP directamente desde la interfaz de configuración.

1.  Abre **Cursor Settings** (`Cmd + ,` o `Ctrl + ,`).
2.  Navega a **General \> MCP**.
3.  Haz clic en **"Add New MCP Server"**.
4.  Rellena los campos:

| Campo | Valor |
| :--- | :--- |
| **Name** | `android-adb` |
| **Type** | `command` (stdio) |
| **Command** | `/RUTA/ABSOLUTA/.../build/install/adb-mcp-server/bin/adb-mcp-server` |

5.  El indicador debería ponerse en verde (Connected). Si falla, revisa la consola de "Output" \> "MCP".

-----

## 3\. GitHub Copilot (VS Code)

Actualmente, Copilot en VS Code consume herramientas MCP a través de extensiones o configuración experimental. El método más robusto hoy es vía la extensión **"Model Context Protocol"** en VS Code, que inyecta el contexto a Copilot.

**Archivo:** `.vscode/settings.json` (Workspace) o Settings Globales.

```json
{
    "mcp.servers": {
        "android-adb": {
            "command": "/RUTA/ABSOLUTA/.../build/install/adb-mcp-server/bin/adb-mcp-server",
            "args": []
        }
    }
}
```

*Reinicia VS Code tras aplicar el cambio.*

-----

## 4\. Gemini CLI / Custom Agents

Si estás usando un CLI wrapper para Gemini que soporte MCP (o tu propio script de Python/Node con el SDK de Gemini), generalmente se pasan como argumentos al iniciar el agente.

**Ejemplo de invocación (Flag style):**

```bash
gemini-cli chat \
  --mcp-server "android-adb=/RUTA/ABSOLUTA/.../bin/adb-mcp-server" \
  --system-prompt "You have access to Android ADB tools. Use them to help me debug."
```

**Si usas un archivo de configuración (`mcp_config.json`):**

```json
{
  "mcpServers": {
    "adb": {
      "command": "java",
      "args": ["-jar", "/RUTA/ABSOLUTA/build/libs/adb-mcp-server-all.jar"]
    }
  }
}
```

-----

## 🛠 Troubleshooting Común

**Error: "adb command not found"**
El servidor MCP arranca, pero cuando llama a `adb`, falla.

  * **Solución:** En tu código Kotlin (`AdbClient`), usa la ruta absoluta al binario ADB (ej. `/Users/usuario/Library/Android/sdk/platform-tools/adb`) O asegura pasar el PATH correcto en la configuración `env` del cliente (ver sección Claude).

**Error: "Connection refused / EACCES"**
Permisos de ejecución faltantes.

  * **Solución:** `chmod +x ./build/install/adb-mcp-server/bin/adb-mcp-server`
