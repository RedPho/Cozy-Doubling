package com.grepho.cozydoubling.features.room

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.grepho.cozydoubling.core.profile.ProfileRepository

// --- THE SCREEN ENTRY POINT ---
@Composable
fun FocusRoomScreen(
    onNavigateToSummary: (String) -> Unit,
    viewModel: FocusRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile by ProfileRepository.profile.collectAsState()
    val myName = profile?.displayName ?: "You"

    val handleExit = {
        viewModel.finishWork { id ->
            onNavigateToSummary(id)
        }
    }

    // Purely UI-driven states
    var isTaskListExpanded by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf("") }

    // Dynamically calculate your own avatar stats based on the current task list
    val currentUserParticipant = RoomParticipant(
        id = "self",
        name = myName,
        activeTaskText = uiState.tasks.find { it.id == uiState.activeTaskId }?.text ?: "No active task",
        completedTasks = uiState.tasks.count { it.isCompleted },
        totalTasks = uiState.tasks.size
    )

    BackHandler(onBack = handleExit)

    // Combine mock users with yourself
    val allParticipants = uiState.otherParticipants + currentUserParticipant

    FocusRoomPage(
        uiState = uiState,
        allParticipants = allParticipants,
        isTaskListExpanded = isTaskListExpanded,
        newTaskText = newTaskText,
        onToggleExpand = { isTaskListExpanded = !isTaskListExpanded },
        onNewTaskTextChange = { newTaskText = it },
        onTaskClick = { viewModel.onTaskClick(it) },
        onTaskToggleStatus = { viewModel.onTaskToggleStatus(it) },
        onAddTask = {
            viewModel.onAddTask(newTaskText)
            newTaskText = ""
        },
        onExitClick = handleExit
    )
}

// --- THE UI COMPONENT ---
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRoomPage(
    uiState: FocusRoomUiState,
    allParticipants: List<RoomParticipant>,
    isTaskListExpanded: Boolean,
    newTaskText: String,
    onToggleExpand: () -> Unit,
    onNewTaskTextChange: (String) -> Unit,
    onTaskClick: (String) -> Unit,
    onTaskToggleStatus: (String) -> Unit,
    onAddTask: () -> Unit,
    onExitClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { /* Empty or very minimal as per design */ },
                navigationIcon = {
                    IconButton(onClick = onExitClick) {
                        // Leaf logo as back button (or use a standard back icon)
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave", tint = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        },
        bottomBar = {
            TaskBottomSheet(
                tasks = uiState.tasks,
                activeTaskId = uiState.activeTaskId,
                onTaskClick = onTaskClick,
                onTaskToggleStatus = onTaskToggleStatus,
                newTaskText = newTaskText,
                onNewTaskTextChange = onNewTaskTextChange,
                onAddTask = onAddTask
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Cozy Room",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Groups,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "${allParticipants.size} focusing now",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // The Grid
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(allParticipants) { participant ->
                    ParticipantCard(participant)
                }
            }
        }
    }
}

@Composable
fun ParticipantCard(participant: RoomParticipant) {
    val progress = if (participant.totalTasks > 0) {
        participant.completedTasks.toFloat() / participant.totalTasks.toFloat()
    } else 0f

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 2.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                // Progress Ring
                CircularProgressIndicator(
                    progress = { 1f },
                    modifier = Modifier.size(80.dp),
                    color = Color.LightGray.copy(alpha = 0.2f),
                    strokeWidth = 6.dp
                )
                CircularProgressIndicator(
                    progress = { progress },
                    modifier = Modifier.size(80.dp),
                    color = MaterialTheme.colorScheme.primary,
                    strokeWidth = 6.dp,
                    strokeCap = StrokeCap.Round
                )
                // Initials Circle
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.secondaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = participant.name.take(2).uppercase(),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = participant.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = "${participant.activeTaskText} • ${participant.completedTasks}/${participant.totalTasks} done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 2
            )
        }
    }
}

@Composable
fun TaskBottomSheet(
    tasks: List<FocusTask>,
    activeTaskId: String?,
    onTaskClick: (String) -> Unit,
    onTaskToggleStatus: (String) -> Unit,
    newTaskText: String,
    onNewTaskTextChange: (String) -> Unit,
    onAddTask: () -> Unit
) {
    val completedCount = tasks.count { it.isCompleted }

    Surface(
        modifier = Modifier.fillMaxWidth().imePadding(),
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        shadowElevation = 16.dp
    ) {
        Column(modifier = Modifier.padding(24.dp)) {
            // --- Title & Progress Pill ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "My Focus Tasks",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = CircleShape
                ) {
                    Text(
                        text = "$completedCount/${tasks.size} Done",
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Task List ---
            LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                items(tasks) { task ->
                    val isActive = task.id == activeTaskId

                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onTaskClick(task.id) },
                        shape = RoundedCornerShape(16.dp),
                        color = when {
                            isActive -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.2f)
                            task.isCompleted -> Color.Transparent
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                        },
                        border = if (isActive)
                            BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                        else BorderStroke(1.dp, Color.LightGray.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onTaskToggleStatus(task.id) }
                            )
                            Text(
                                text = task.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyLarge.copy(
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else null,
                                    fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal
                                ),
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                            )

                            if (isActive && !task.isCompleted) {
                                Icon(
                                    imageVector = Icons.Default.FlashOn,
                                    contentDescription = "Active",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // --- Input Area ---
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextField(
                    value = newTaskText,
                    onValueChange = onNewTaskTextChange,
                    placeholder = { Text("What are you working on next?") },
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    shape = CircleShape,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        unfocusedPlaceholderColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                    )
                )
                Spacer(modifier = Modifier.width(12.dp))
                FloatingActionButton(
                    onClick = onAddTask,
                    shape = CircleShape,
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White,
                    modifier = Modifier.size(48.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add")
                }
            }
        }
    }
}