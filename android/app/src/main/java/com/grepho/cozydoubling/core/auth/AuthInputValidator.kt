package com.grepho.cozydoubling.core.auth

/** Client-side checks mirrored by the backend ([AUTH-02], [AUTH-08]). */
object AuthInputValidator {
    const val MIN_PASSWORD_LENGTH = 8
    const val MAX_PASSWORD_LENGTH = 128

    private val emailPattern = Regex("""^[^@\s]+@[^@\s]+\.[^@\s]+$""")

    enum class Problem { EMAIL_INVALID, PASSWORD_EMPTY, PASSWORD_TOO_SHORT, PASSWORD_TOO_LONG, PASSWORDS_DIFFER }

    fun normalizeEmail(email: String): String = email.trim().lowercase()

    fun validateSignIn(email: String, password: String): Problem? = when {
        !emailPattern.matches(normalizeEmail(email)) -> Problem.EMAIL_INVALID
        password.isEmpty() -> Problem.PASSWORD_EMPTY
        else -> null
    }

    fun validateRegistration(email: String, password: String, confirmPassword: String): Problem? = when {
        !emailPattern.matches(normalizeEmail(email)) -> Problem.EMAIL_INVALID
        password.length < MIN_PASSWORD_LENGTH -> Problem.PASSWORD_TOO_SHORT
        password.length > MAX_PASSWORD_LENGTH -> Problem.PASSWORD_TOO_LONG
        password != confirmPassword -> Problem.PASSWORDS_DIFFER
        else -> null
    }
}
