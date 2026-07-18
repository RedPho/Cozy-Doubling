package com.grepho.cozydoubling.features.room

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
    onNavigateToSummary: () -> Unit,
    viewModel: FocusRoomViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val profile by ProfileRepository.profile.collectAsState()
    val myName = profile?.displayName ?: "You"

    val handleExit = {
        viewModel.finishWork {
            onNavigateToSummary() // Now it's a function call!
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
                title = { Text("Cozy Room") },
                navigationIcon = {
                    IconButton(onClick = onExitClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Leave Room")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            TaskBottomSheet(
                tasks = uiState.tasks,
                activeTaskId = uiState.activeTaskId,
                isExpanded = isTaskListExpanded,
                onToggleExpand = onToggleExpand,
                onTaskClick = onTaskClick,
                onTaskToggleStatus = onTaskToggleStatus,
                newTaskText = newTaskText,
                onNewTaskTextChange = onNewTaskTextChange,
                onAddTask = onAddTask
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(allParticipants) { participant ->
                    ParticipantAvatar(participant)
                }
            }
        }
    }
}

@Composable
fun ParticipantAvatar(participant: RoomParticipant) {
    val progress = if (participant.totalTasks > 0) {
        participant.completedTasks.toFloat() / participant.totalTasks.toFloat()
    } else {
        0f
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.fillMaxWidth()
    ) {
        Box(contentAlignment = Alignment.Center) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 4.dp
            )
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = Color.Transparent
            )

            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = participant.name.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = participant.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Text(
            text = participant.activeTaskText,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
fun TaskBottomSheet(
    tasks: List<FocusTask>,
    activeTaskId: String?,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onTaskClick: (String) -> Unit,
    onTaskToggleStatus: (String) -> Unit,
    newTaskText: String,
    onNewTaskTextChange: (String) -> Unit,
    onAddTask: () -> Unit
) {
    val activeTask = tasks.find { it.id == activeTaskId }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceVariant,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Current Focus",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = activeTask?.text ?: "No active task. Add one below!",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = "Toggle Task List",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(visible = isExpanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .padding(vertical = 8.dp)
                ) {
                    items(tasks) { task ->
                        val isActive = task.id == activeTaskId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { onTaskClick(task.id) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onTaskToggleStatus(task.id) }
                            )
                            Text(
                                text = task.text,
                                modifier = Modifier.weight(1f),
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    textDecoration = if (task.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                                ),
                                color = if (task.isCompleted) MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedTextField(
                value = newTaskText,
                onValueChange = onNewTaskTextChange,
                placeholder = { Text("What are you working on?") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { onAddTask() }),
                trailingIcon = {
                    IconButton(onClick = onAddTask) {
                        Icon(Icons.Default.Add, contentDescription = "Add Task")
                    }
                },
                shape = RoundedCornerShape(16.dp)
            )
        }
    }
}