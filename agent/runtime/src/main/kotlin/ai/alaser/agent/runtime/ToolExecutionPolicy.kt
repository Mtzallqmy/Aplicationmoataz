/*
 * Adapted from Lociant's Android device-layer ToolPolicy.
 * Copyright (c) 2026 Lociant Contributors
 * SPDX-License-Identifier: MIT
 * See app/src/main/assets/licenses/LOCIANT-MIT.txt for the complete license.
 */
package ai.alaser.agent.runtime

enum class ToolExposure { READ, ACTION }

data class ToolExecutionPolicy(
    val local: Boolean = true,
    val remoteAllowed: Boolean = false,
    val sideEffect: Boolean = false,
    val destructive: Boolean = false,
    val openWorld: Boolean = false,
) {
    val exposure: ToolExposure
        get() = if (sideEffect || destructive || openWorld) ToolExposure.ACTION else ToolExposure.READ
}
