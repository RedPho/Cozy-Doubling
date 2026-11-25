package com.grepho.cozydoubling.core.validation

/**
 * Pure domain guardrails for accessibility font scaling and text field lengths
 * across Android UI controls, ViewModels, and backend validation.
 */
object TextLimits {
    const val MIN_FONT_SCALE = 0.85f
    const val MAX_FONT_SCALE = 1.25f

    const val MAX_DISPLAY_NAME_LENGTH = 30
    const val MAX_TASK_TEXT_LENGTH = 120
    const val MAX_FRIEND_CODE_LENGTH = 9
    const val MAX_REPORT_REASON_LENGTH = 200
    const val MAX_EMAIL_LENGTH = 254
    const val MAX_PASSWORD_LENGTH = 128

    fun clampFontScale(fontScale: Float): Float =
        fontScale.coerceIn(MIN_FONT_SCALE, MAX_FONT_SCALE)

    fun limitInput(value: String, maxLength: Int): String =
        if (value.length <= maxLength) value else value.take(maxLength)

    fun sanitizeDisplayName(value: String): String =
        value.trim().take(MAX_DISPLAY_NAME_LENGTH)

    fun sanitizeTaskText(value: String): String =
        value.trim().take(MAX_TASK_TEXT_LENGTH)

    fun sanitizeFriendCode(value: String): String =
        value.trim().take(MAX_FRIEND_CODE_LENGTH)
}
