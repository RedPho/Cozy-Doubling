package com.grepho.cozydoubling.features.summary

data class SummaryUiState(
    val focusedMinutes: Int = 0,
    val tasksFinished: Int = 0,
    val leavesEarned: Int = 0,
    val endReason: String? = null,
    val wasCapped: Boolean = false,
    val isLoading: Boolean = false,
    val wasInterrupted: Boolean = false,
    val loadError: Boolean = false
)

enum class SummaryHeaderKind {
    NORMAL,
    APP_CLOSED,
    ABANDONED
}

enum class SummaryNoticeKind {
    NONE,
    APP_CLOSED,
    ABANDONED_DISCONNECT,
    OFFLINE_GAP,
    CAPPED_MAX_DURATION,
    LOAD_ERROR
}

data class SummaryPresentation(
    val headerKind: SummaryHeaderKind,
    val noticeKind: SummaryNoticeKind,
    val showNoticeAtTop: Boolean
)

fun resolveSummaryPresentation(uiState: SummaryUiState): SummaryPresentation {
    val headerKind = when {
        uiState.endReason == "abandoned_disconnect" -> SummaryHeaderKind.ABANDONED
        uiState.endReason == "app_closed" || uiState.wasInterrupted -> SummaryHeaderKind.APP_CLOSED
        else -> SummaryHeaderKind.NORMAL
    }
    val noticeKind = when {
        uiState.loadError -> SummaryNoticeKind.LOAD_ERROR
        uiState.endReason == "app_closed" -> SummaryNoticeKind.APP_CLOSED
        uiState.endReason == "abandoned_disconnect" -> SummaryNoticeKind.ABANDONED_DISCONNECT
        uiState.endReason == "capped_max_duration" -> SummaryNoticeKind.CAPPED_MAX_DURATION
        uiState.endReason == "completed_with_offline_gap" || uiState.wasCapped -> SummaryNoticeKind.OFFLINE_GAP
        uiState.wasInterrupted -> SummaryNoticeKind.APP_CLOSED
        else -> SummaryNoticeKind.NONE
    }
    val showNoticeAtTop = noticeKind in setOf(
        SummaryNoticeKind.APP_CLOSED,
        SummaryNoticeKind.ABANDONED_DISCONNECT,
        SummaryNoticeKind.LOAD_ERROR
    )
    return SummaryPresentation(
        headerKind = headerKind,
        noticeKind = noticeKind,
        showNoticeAtTop = showNoticeAtTop
    )
}