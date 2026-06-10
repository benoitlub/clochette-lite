package com.feuch.clochette

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class ClochetteAccessibilityService : AccessibilityService() {
    private val memory by lazy { ClochetteMemory(this) }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName?.toString().orEmpty()
        if (packageName.isBlank()) return

        memory.add(
            ClochetteMemoryEntry(
                context = "assistive_clochette",
                observedSignal = "window_change_package=$packageName",
                project = null,
                energy = null,
                clochetteLine = null,
                userReaction = null,
                result = "assistive_signal",
            ),
        )
    }

    override fun onInterrupt() = Unit
}
