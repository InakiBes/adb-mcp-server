# Implementation Plan: Restrict & Enhance ADB MCP Tools

This document outlines the tasks required to improve the security and usability of the ADB MCP (Model Context Protocol) server. The goal is to remove the generic and potentially unsafe `adb_shell` tool and replace it with a set of specific, controlled tools for common Android development and testing tasks.

## 1. Security & Restriction
- [ ] **Remove `adb_shell` tool**: Remove the generic shell execution tool to restrict broad access and control the capabilities of the MCP.

## 2. Basic Management Tools
Implement the following tools for basic app and device management:
- [ ] **`uninstall_package`**: Uninstall an application (with an option to keep data/cache).
- [ ] **`start_activity`**: Launch an application or a specific Activity.
- [ ] **`gradle_assemble`**: Compile the Android project and produce APK(s).
- [ ] **`force_stop`**: Force stop a running application.
- [ ] **`clear_app_data`**: Clear application data and state to ensure clean test environments.
- [ ] **`current_activity`**: Retrieve the name of the Activity and task currently in the foreground.
- [ ] **`deep_link`**: Open the corresponding application using a deep link or specific intent URI.

## 3. UI Interaction Tools
Implement specific tools to interact with the device UI:
- [ ] **`ui_tap`**: Simulate a tap/click event at specific coordinates or on a UI selector.
- [ ] **`ui_swipe`**: Simulate swipe gestures on the screen.
- [ ] **`ui_input_text`**: Input text into the currently focused field.
- [ ] **`ui_back`**: Simulate the system "Back" button action.
- [ ] **`wait_for_ui`**: Wait for a specific UI element to appear on the screen before proceeding.

## 4. Permission Management Tools
Implement tools to manage application runtime permissions:
- [ ] **`grant_permission`**: Grant specific runtime permissions to an application.
- [ ] **`revoke_permission`**: Revoke specific runtime permissions from an application.
