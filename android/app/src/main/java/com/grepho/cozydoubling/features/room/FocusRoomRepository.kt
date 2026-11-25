package com.grepho.cozydoubling.features.room

import kotlinx.coroutines.flow.Flow

interface FocusRoomRepository {
    fun joinRoom(presenceKey: String): Flow<List<ParticipantPresence>>
    suspend fun subscribe()
    fun getChannelStatus(): Flow<RoomConnectionStatus>
    suspend fun broadcastPresence(presence: ParticipantPresence)
    fun listenForBroadcasts(): Flow<ParticipantPresence>
    suspend fun updatePresence(presence: ParticipantPresence)
    suspend fun leaveRoom(presenceKey: String? = null)
    suspend fun startSession(): String?
    suspend fun finishSession(sessionId: String, tasksCompleted: Int, taskText: String, endReason: String? = null)
    suspend fun fetchSession(sessionId: String): FocusSession?
    fun observeSessionEvents(): Flow<RoomSessionEvent>
}
