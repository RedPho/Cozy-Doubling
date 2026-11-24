package com.grepho.cozydoubling.data.remote

import androidx.compose.ui.graphics.Color
import com.grepho.cozydoubling.core.profile.Profile
import com.grepho.cozydoubling.core.theming.ThemePalette
import com.grepho.cozydoubling.features.room.FocusSession
import com.grepho.cozydoubling.features.room.ParticipantPresence
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("player_tag") val playerTag: String,
    @SerialName("equipped_theme_id") val equippedThemeId: String? = null,
    val leaves: Long = 0,
    @SerialName("created_at") val createdAt: String? = null,
    @SerialName("total_minutes_focused") val totalMinutesFocused: Long = 0,
    @SerialName("is_supporter") val isSupporter: Boolean = false,
    @SerialName("fcm_token") val fcmToken: String? = null
) {
    fun toDomain(): Profile = Profile(
        id = id,
        displayName = displayName,
        playerTag = playerTag,
        equippedThemeId = equippedThemeId,
        leaves = leaves,
        createdAt = createdAt,
        totalMinutesFocused = totalMinutesFocused,
        isSupporter = isSupporter,
        fcmToken = fcmToken
    )
}

@Serializable
data class ParticipantPresenceDto(
    val id: String,
    val name: String = "Cozy Doubler",
    @SerialName("active_task") val activeTaskText: String? = "",
    @SerialName("completed_tasks") val completedTasks: Int = 0,
    @SerialName("total_tasks") val totalTasks: Int = 0
) {
    fun toDomain(): ParticipantPresence = ParticipantPresence(
        id = id,
        name = name,
        activeTaskText = activeTaskText,
        completedTasks = completedTasks,
        totalTasks = totalTasks
    )

    companion object {
        fun fromDomain(domain: ParticipantPresence): ParticipantPresenceDto = ParticipantPresenceDto(
            id = domain.id,
            name = domain.name,
            activeTaskText = domain.activeTaskText,
            completedTasks = domain.completedTasks,
            totalTasks = domain.totalTasks
        )
    }
}

@Serializable
data class FocusSessionDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("tasks_completed") val tasksCompleted: Int = 0,
    @SerialName("earned_leaves") val earnedLeaves: Long = 0,
    @SerialName("is_processed") val isProcessed: Boolean = false,
    @SerialName("duration_minutes") val durationMinutes: Int = 0
) {
    fun toDomain(): FocusSession = FocusSession(
        id = id,
        userId = userId,
        startTime = startTime,
        endTime = endTime,
        tasksCompleted = tasksCompleted,
        earnedLeaves = earnedLeaves,
        isProcessed = isProcessed,
        durationMinutes = durationMinutes
    )
}

fun parseHexColor(hex: String): Color {
    val clean = hex.removePrefix("#").removePrefix("0x").removePrefix("0X")
    val colorLong = when (clean.length) {
        6 -> 0xFF000000L or clean.toLong(16)
        8 -> clean.toLong(16)
        else -> 0xFF000000L
    }
    return Color(colorLong)
}

@Serializable
data class AuthResponseDto(
    @SerialName("access_token") val accessToken: String,
    @SerialName("token_type") val tokenType: String = "bearer"
)

@Serializable
data class LoginRequestDto(
    val email: String,
    val password: String
)

@Serializable
data class RegisterRequestDto(
    val email: String,
    val password: String,
    @SerialName("display_name") val displayName: String? = null
)

@Serializable
data class GoogleAuthRequestDto(
    @SerialName("id_token") val idToken: String
)

@Serializable
data class UpdateProfileRequestDto(
    @SerialName("display_name") val displayName: String
)

@Serializable
data class FcmTokenRequestDto(
    @SerialName("fcm_token") val fcmToken: String?
)

@Serializable
data class StartSessionResponseDto(
    @SerialName("session_id") val sessionId: String
)

@Serializable
data class FinishSessionRequestDto(
    @SerialName("tasks_completed") val tasksCompleted: Int,
    @SerialName("last_task_text") val lastTaskText: String
)

@Serializable
data class FriendRequestDto(
    @SerialName("player_tag") val playerTag: String
)

@Serializable
data class FriendStoryDto(
    @SerialName("friend_id") val friendId: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("player_tag") val playerTag: String,
    @SerialName("duration_minutes") val durationMinutes: Int? = null,
    @SerialName("end_time") val endTime: String? = null,
    @SerialName("last_task_text") val lastTaskText: String? = null,
    @SerialName("accepted_at") val acceptedAt: String? = null
)

@Serializable
data class FriendProfileDto(
    val id: String,
    @SerialName("display_name") val displayName: String,
    @SerialName("player_tag") val playerTag: String
)

@Serializable
data class ThemePaletteDto(
    val primary: String,
    @SerialName("on_primary") val onPrimary: String,
    @SerialName("primary_container") val primaryContainer: String,
    @SerialName("on_primary_container") val onPrimaryContainer: String,
    val secondary: String,
    @SerialName("on_secondary") val onSecondary: String,
    @SerialName("secondary_container") val secondaryContainer: String,
    @SerialName("on_secondary_container") val onSecondaryContainer: String,
    val tertiary: String,
    @SerialName("on_tertiary") val onTertiary: String,
    @SerialName("tertiary_container") val tertiaryContainer: String,
    @SerialName("on_tertiary_container") val onTertiaryContainer: String,
    val surface: String,
    @SerialName("on_surface") val onSurface: String,
    val background: String,
    @SerialName("on_background") val onBackground: String,
    @SerialName("surface_variant") val surfaceVariant: String,
    @SerialName("on_surface_variant") val onSurfaceVariant: String
) {
    fun toThemePalette(): ThemePalette = ThemePalette(
        primary = parseHexColor(primary),
        onPrimary = parseHexColor(onPrimary),
        primaryContainer = parseHexColor(primaryContainer),
        onPrimaryContainer = parseHexColor(onPrimaryContainer),
        secondary = parseHexColor(secondary),
        onSecondary = parseHexColor(onSecondary),
        secondaryContainer = parseHexColor(secondaryContainer),
        onSecondaryContainer = parseHexColor(onSecondaryContainer),
        tertiary = parseHexColor(tertiary),
        onTertiary = parseHexColor(onTertiary),
        tertiaryContainer = parseHexColor(tertiaryContainer),
        onTertiaryContainer = parseHexColor(onTertiaryContainer),
        surface = parseHexColor(surface),
        onSurface = parseHexColor(onSurface),
        background = parseHexColor(background),
        onBackground = parseHexColor(onBackground),
        surfaceVariant = parseHexColor(surfaceVariant),
        onSurfaceVariant = parseHexColor(onSurfaceVariant)
    )
}

@Serializable
data class ShopItemDto(
    val id: String,
    val name: String,
    @SerialName("category_id") val categoryId: String,
    @SerialName("is_premium") val isPremium: Boolean,
    @SerialName("leaf_price") val leafPrice: Int,
    @SerialName("is_owned") val isOwned: Boolean,
    @SerialName("is_equipped") val isEquipped: Boolean,
    @SerialName("iap_id") val iapId: String? = null,
    val palette: ThemePaletteDto? = null
)

@Serializable
data class BuyItemRequestDto(
    @SerialName("item_id") val itemId: String
)

@Serializable
data class EquipItemRequestDto(
    @SerialName("theme_id") val themeId: String
)

@Serializable
data class BlockUserRequestDto(
    @SerialName("target_id") val targetId: String
)

@Serializable
data class ReportUserRequestDto(
    @SerialName("target_id") val targetId: String,
    val reason: String
)

@Serializable
data class BlockedUsersDto(
    @SerialName("blocked_user_ids") val blockedUserIds: List<String>
)

@Serializable
data class SyncSubscriptionRequestDto(
    @SerialName("is_supporter") val isSupporter: Boolean? = null
)

@Serializable
data class WsRoomMessage(
    val type: String,
    val participants: List<ParticipantPresenceDto>? = null,
    val presence: ParticipantPresenceDto? = null,
    @SerialName("presence_id") val presenceId: String? = null
)

@Serializable
data class WsPresenceUpdate(
    val type: String = "presence_update",
    val presence: ParticipantPresenceDto
)

@Serializable
data class WsSafetyMessage(
    val type: String,
    @SerialName("blocked_id") val blockedId: String? = null
)
