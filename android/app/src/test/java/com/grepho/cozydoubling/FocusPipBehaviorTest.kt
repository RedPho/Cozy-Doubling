package com.grepho.cozydoubling

import com.grepho.cozydoubling.core.room.shouldRestoreFocusRoomOnPipExit
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class FocusPipBehaviorTest {

    private fun resolveMainActivityFile(): File {
        val candidates = listOf(
            File("src/main/java/com/grepho/cozydoubling/core/MainActivity.kt"),
            File("app/src/main/java/com/grepho/cozydoubling/core/MainActivity.kt")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate MainActivity.kt from working dir: ${File(".").absolutePath}")
    }

    @Test
    fun shouldRestoreFocusRoomOnPipExit_handlesAllTransitionsCorrectly() {
        // Expanding PiP back to fullscreen while started and session is active -> restore/keep Focus Room
        assertTrue(
            shouldRestoreFocusRoomOnPipExit(
                wasInPip = true,
                isNowInPip = false,
                isActivityStarted = true,
                hasActiveSession = true
            )
        )

        // Entering PiP -> false
        assertFalse(
            shouldRestoreFocusRoomOnPipExit(
                wasInPip = false,
                isNowInPip = true,
                isActivityStarted = true,
                hasActiveSession = true
            )
        )

        // Dismissing PiP with X while Activity is stopped/finishing -> false
        assertFalse(
            shouldRestoreFocusRoomOnPipExit(
                wasInPip = true,
                isNowInPip = false,
                isActivityStarted = false,
                hasActiveSession = true
            )
        )

        // Exiting PiP with no active focus session -> false
        assertFalse(
            shouldRestoreFocusRoomOnPipExit(
                wasInPip = true,
                isNowInPip = false,
                isActivityStarted = true,
                hasActiveSession = false
            )
        )

        // Already fullscreen non-transition -> false
        assertFalse(
            shouldRestoreFocusRoomOnPipExit(
                wasInPip = false,
                isNowInPip = false,
                isActivityStarted = true,
                hasActiveSession = true
            )
        )
    }

    @Test
    fun mainActivity_keepsCozyDoublingAppMountedWhenPipOverlayIsDisplayed() {
        val content = resolveMainActivityFile().readText()
        val cozyIndex = content.indexOf("CozyDoublingApp(")
        val pipCheckIndex = content.indexOf("if (isInPipMode && snapshot != null)")

        assertTrue("CozyDoublingApp should be present in MainActivity.kt", cozyIndex >= 0)
        assertTrue("PiP overlay check should be present in MainActivity.kt", pipCheckIndex >= 0)
        assertTrue(
            "CozyDoublingApp must be composed unconditionally before PiP overlay so NavController backstack survives PiP",
            cozyIndex < pipCheckIndex
        )
    }
}
