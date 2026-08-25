/*
 * Adapted from Agora by newo-ether.
 * Copyright (c) 2026 newo-ether
 * SPDX-License-Identifier: MIT
 * See app/src/main/assets/licenses/AGORA-MIT.txt for the complete license.
 */
package ai.alaser.agent.runtime

/** Defensively combines incremental, repeated, or full-snapshot tool argument chunks. */
class ToolArgumentAccumulator(initial: String = "") {
    private val builder = StringBuilder(initial)

    val isEmpty: Boolean get() = builder.isEmpty()

    fun append(fragment: String?) {
        if (fragment.isNullOrEmpty()) return
        val current = builder.toString()
        if (current.isEmpty()) {
            builder.append(fragment)
            return
        }
        if (current.length >= MIN_SNAPSHOT_OVERLAP && fragment.length >= MIN_SNAPSHOT_OVERLAP) {
            if (fragment == current) return
            if (fragment.length > current.length && fragment.startsWith(current)) {
                builder.setLength(0)
                builder.append(fragment)
                return
            }
            if (current.startsWith(fragment)) return
        }
        builder.append(fragment)
    }

    override fun toString(): String = builder.toString()

    private companion object {
        const val MIN_SNAPSHOT_OVERLAP = 2
    }
}
