package com.grepho.cozydoubling

import androidx.lifecycle.SavedStateHandle
import com.grepho.cozydoubling.core.validation.TextLimits
import com.grepho.cozydoubling.data.fake.FakeAuthRepository
import com.grepho.cozydoubling.data.fake.FakeFocusRoomRepository
import com.grepho.cozydoubling.data.fake.FakeProfileRepository
import com.grepho.cozydoubling.data.fake.FakeSafetyRepository
import com.grepho.cozydoubling.data.fake.NoOpFocusSessionLifecycleController
import com.grepho.cozydoubling.features.room.FocusRoomViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TextLimitsTest {

    private fun resolveThemeFile(): File {
        val candidates = listOf(
            File("src/main/java/com/grepho/cozydoubling/ui/theme/Theme.kt"),
            File("app/src/main/java/com/grepho/cozydoubling/ui/theme/Theme.kt")
        )
        return candidates.firstOrNull { it.exists() && it.isFile }
            ?: error("Could not locate Theme.kt from working dir: ${File(".").absolutePath}")
    }

    @Test
    fun clampFontScale_boundsExtremeAccessibilityScales() {
        assertEquals(TextLimits.MIN_FONT_SCALE, TextLimits.clampFontScale(0.5f), 0.001f)
        assertEquals(1.0f, TextLimits.clampFontScale(1.0f), 0.001f)
        assertEquals(1.15f, TextLimits.clampFontScale(1.15f), 0.001f)
        assertEquals(TextLimits.MAX_FONT_SCALE, TextLimits.clampFontScale(1.25f), 0.001f)
        assertEquals(TextLimits.MAX_FONT_SCALE, TextLimits.clampFontScale(2.0f), 0.001f)
    }

    @Test
    fun sanitizeAndLimitHelpers_enforceExactCharacterBounds() {
        assertEquals("abc", TextLimits.limitInput("abcdef", 3))
        assertEquals("A".repeat(TextLimits.MAX_DISPLAY_NAME_LENGTH), TextLimits.sanitizeDisplayName("  ${"A".repeat(50)}  "))
        assertEquals("T".repeat(TextLimits.MAX_TASK_TEXT_LENGTH), TextLimits.sanitizeTaskText("  ${"T".repeat(200)}  "))
        assertEquals("K7M2P9X4E", TextLimits.sanitizeFriendCode("  K7M2P9X4EXTRA  "))
        assertEquals("123456789", TextLimits.sanitizeFriendCode("1234567890ABCDEF"))
    }

    @Test
    fun cozyDoublingTheme_appliesLocalDensityFontScaleCap() {
        val content = resolveThemeFile().readText()
        assertTrue(content.contains("CompositionLocalProvider(LocalDensity provides cappedDensity)"))
        assertTrue(content.contains("TextLimits.clampFontScale(currentDensity.fontScale)"))
    }

    @Test
    fun focusRoomViewModel_onAddTaskClampsLongTaskInput() = runBlocking {
        val authRepo = FakeAuthRepository()
        val profileRepo = FakeProfileRepository(authRepo)
        val roomRepo = FakeFocusRoomRepository(profileRepo)
        val scope = CoroutineScope(Dispatchers.Unconfined + SupervisorJob())
        try {
            val vm = FocusRoomViewModel(
                savedStateHandle = SavedStateHandle(mapOf("recovery" to false, "tasks_json" to "[]")),
                roomRepository = roomRepo,
                profileRepository = profileRepo,
                safetyRepository = FakeSafetyRepository(),
                authRepository = authRepo,
                sessionController = NoOpFocusSessionLifecycleController(),
                scopeOverride = scope
            )
            vm.onAddTask("  ${"B".repeat(200)}  ")
            assertEquals(1, vm.uiState.value.tasks.size)
            assertEquals("B".repeat(TextLimits.MAX_TASK_TEXT_LENGTH), vm.uiState.value.tasks.first().text)
        } finally {
            scope.cancel()
        }
    }
}
