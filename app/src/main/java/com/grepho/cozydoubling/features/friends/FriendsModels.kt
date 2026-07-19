package com.grepho.cozydoubling.features.friends

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class FriendUiState(
    @SerialName("friend_id") // Matches the 'friend_id' column in our SQL View
    val id: String,
    @SerialName("display_name")
    val name: String,
    @SerialName("player_tag")
    val playerTag: String,

    // These are now nullable so friends without sessions can appear!
    @SerialName("duration_minutes")
    val lastSessionDuration: Int? = null,
    @SerialName("end_time")
    val lastSessionDate: String? = null,
    @SerialName("last_task_text")
    val lastTaskText: String? = null,

    @SerialName("accepted_at")
    val friendsSince: String? = null
)

@Serializable
data class Friendship(
    @SerialName("user_id_1")
    val userId1: String,
    @SerialName("user_id_2")
    val userId2: String,
    @SerialName("requester_id")
    val requesterId: String,
    val status: String,
    @SerialName("accepted_at")
    val acceptedAt: String? = null
)

@Serializable
data class FriendProfileDto(
    val id: String,
    @SerialName("display_name")
    val name: String,
    @SerialName("player_tag")
    val playerTag: String,
    // Embed the latest session data
    @SerialName("latest_sessions")
    val latestSession: FocusSessionDto? = null
)

@Serializable
data class FocusSessionDto(
    @SerialName("duration_minutes")
    val duration: Int,
    @SerialName("end_time")
    val endTime: String,
    @SerialName("last_task_text")
    val taskText: String
)