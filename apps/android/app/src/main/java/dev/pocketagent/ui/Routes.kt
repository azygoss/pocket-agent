// SPDX-License-Identifier: GPL-3.0-or-later
package dev.pocketagent.ui

// P05 nav skeleton: Home, Connections, Active Sessions, Agents, Files, Settings.
enum class Route(val label: String) {
    Home("Home"), Connections("Connections"), Sessions("Active Sessions"),
    Agents("Agents"), Files("Files"), Settings("Settings");
}
