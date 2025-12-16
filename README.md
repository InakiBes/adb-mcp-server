**Project:** `adb-mcp-server`
**Runtime:** JVM (Kotlin)
**Integration:** Stdio (JSON-RPC 2.0)

**Overview:**
Este servidor implementa el protocolo MCP para actuar como un puente intermedio entre Modelos de Lenguaje (LLMs) y dispositivos Android conectados físicamente o emulados. Abstrae comandos complejos de ADB CLI en herramientas invocables por la IA, permitiendo flujos de automatización, testing y depuración sin intervención humana directa.

**Capacidades generales:**

  * Listar seriales conectados y elegir destino de comandos.
  * Correr comandos arbitrarios vía `adb shell` para inspección, depuración y automatización.
  * Capturar pantallas (`screencap`) y volcar el árbol de vistas (`uiautomator dump`) para análisis semántico.
  * Instalar APKs y reinstrumentar builds rápidamente en el dispositivo objetivo.

**Tools MCP expuestos:**

| Tool | Parámetros | Devuelve | Caso de uso |
| :--- | :--- | :--- | :--- |
| `list_devices` | — | Lista de seriales conectados (uno por línea) | Descubrir a qué dispositivo apuntar o validar conexión. |
| `adb_shell` | `command` (req.), `deviceId` (opt.) | `stdout` del comando en el dispositivo | Ejecutar `pm list packages`, `am start`, `input tap`, etc. |
| `get_screenshot` | `deviceId` (opt.) | PNG base64 (screencap) | Extraer contexto visual para agentes con visión. |
| `install_apk` | `path` (req.), `deviceId` (opt.) | `"ok"` tras instalación con `adb install -r` | Publicar una build local en caliente. |
| `dump_hierarchy` | `deviceId` (opt.) | XML de `uiautomator dump /dev/tty` | Leer estructura de la UI y planear interacciones. |

`deviceId` es siempre opcional; si se omite, ADB usa el dispositivo por defecto. El `screenshot` viene en base64 listo para decodificar en el cliente MCP.

**Flujos posibles con estos tools:**

  * Ejecutar smoke tests manuales: instalar una APK, lanzar la actividad principal, inspeccionar logs/estado con `adb_shell`.
  * Depurar pantallas: capturar screenshot + árbol XML y pedir a la IA que describa elementos o sugiera taps.
  * Operaciones de mantenimiento: listar dispositivos conectados en CI, limpiar datos de apps, forzar reinicios de paquetes, etc.

### Ejemplos de llamadas `tools/call`

Todas las llamadas usan `method: "tools/call"` con `params.name` como el tool y `params.arguments` como el payload.

`list_devices`

```json
{
  "method": "tools/call",
  "params": { "name": "list_devices", "arguments": {} }
}
```
Resultado típico (`content[0].text`):
```
emulator-5554
0123456789ABCDEF
```

`adb_shell` (con `deviceId` opcional)

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
Resultado (`content[0].text`): por ejemplo `14`.

`get_screenshot`

```json
{
  "method": "tools/call",
  "params": { "name": "get_screenshot", "arguments": { "deviceId": "emulator-5554" } }
}
```
Resultado (`content[0].text`): cadena base64 PNG lista para decodificar.

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
Resultado (`content[0].text`): `ok` si la instalación fue exitosa.

`dump_hierarchy`

```json
{
  "method": "tools/call",
  "params": { "name": "dump_hierarchy", "arguments": { "deviceId": "emulator-5554" } }
}
```
Resultado (`content[0].text`): XML completo de la jerarquía de vistas (`uiautomator dump /dev/tty`).

### Payload de Inicialización MCP (Código)

Esta es la descripción **crítica** que el agente debe devolver en la respuesta al método `initialize`. Es lo que la IA (Cursor/Claude) "leerá" para entender qué es este servidor.

```kotlin
// Dentro de tu respuesta al método 'initialize'
val serverInfo = ServerInfo(
    name = "adb-mcp-server",
    version = "0.1.0"
)

val capabilities = Capabilities(
    tools = ToolsCapability(listChanged = true),
    resources = null, // Opcional si decides exponer logs como recursos
    prompts = null    // Opcional
)
```

**Instrucción para el JSON final:**

```json
{
  "name": "adb-mcp-server",
  "version": "0.1.0",
  "description": "Provides control over Android devices via ADB. Lists devices, runs shell commands, installs APKs, takes screenshots (base64 PNG), and dumps UI hierarchy XML."
}
```
