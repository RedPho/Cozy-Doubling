package com.grepho.cozydoubling.ui.pages

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
import androidx.compose.material.icons.filled.Add
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class FocusTask(
    val id: String,
    val text: String,
    val isCompleted: Boolean = false
)

data class RoomParticipant(
    val id: String,
    val name: String,
    val activeTaskText: String,
    val completedTasks: Int,
    val totalTasks: Int
)


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusRoomPage(
    onLeaveClick: () -> Unit
) {
    // --- Mock State ---
    var tasks by remember {
        mutableStateOf(
            listOf(
                FocusTask("1", "Read chapter 4"),
                FocusTask("2", "Reply to emails", isCompleted = true),
                FocusTask("3", "Write introduction")
            )
        )
    }

    // The active task is the one selected, default to the first incomplete one
    var activeTaskId by remember { mutableStateOf(tasks.firstOrNull { !it.isCompleted }?.id) }
    var isTaskListExpanded by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf("") }

    val participants = listOf(
        RoomParticipant("1", "Alex", "Writing emails", 3, 7),
        RoomParticipant("2", "Jamie", "Studying Math", 1, 4),
        RoomParticipant("3", "You", tasks.find { it.id == activeTaskId }?.text ?: "No active task", tasks.count { it.isCompleted }, tasks.size),

    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cozy Cafe Room") },
                navigationIcon = {
                    IconButton(onClick = onLeaveClick) {
                        Icon(Icons.Default.Close, contentDescription = "Leave Room")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        bottomBar = {
            TaskBottomSheet(
                tasks = tasks,
                activeTaskId = activeTaskId,
                isExpanded = isTaskListExpanded,
                onToggleExpand = { isTaskListExpanded = !isTaskListExpanded },
                onTaskClick = { activeTaskId = it.id },
                onTaskToggleStatus = { toggledTask ->
                    tasks = tasks.map {
                        if (it.id == toggledTask.id) it.copy(isCompleted = !it.isCompleted) else it
                    }
                },
                newTaskText = newTaskText,
                onNewTaskTextChange = { newTaskText = it },
                onAddTask = {
                    if (newTaskText.isNotBlank()) {
                        val newTask = FocusTask(id = System.currentTimeMillis().toString(), text = newTaskText)
                        tasks = tasks + newTask
                        if (activeTaskId == null) activeTaskId = newTask.id
                        newTaskText = ""
                    }
                }
            )
        }
    ) { innerPadding ->
        // --- The Canvas (Middle Area) ---
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3), // 3 users per row
                modifier = Modifier.fillMaxSize().padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                items(participants) { participant ->
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
        // Avatar with Progress Ring
        Box(contentAlignment = Alignment.Center) {
            // Background track (optional, makes it look nicer)
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 4.dp
            )
            // Actual progress
            CircularProgressIndicator(
                progress = { progress },
                modifier = Modifier.size(72.dp),
                color = MaterialTheme.colorScheme.primary,
                strokeWidth = 4.dp,
                trackColor = Color.Transparent
            )

            // The Circle Character
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

        // Name
        Text(
            text = participant.name,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        // Active Task
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
    onTaskClick: (FocusTask) -> Unit,
    onTaskToggleStatus: (FocusTask) -> Unit,
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
            // --- Active Task Display & Expand Arrow ---
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

            // --- Expanded Task List ---
            AnimatedVisibility(visible = isExpanded) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp) // Prevents it from taking over the whole screen
                        .padding(vertical = 8.dp)
                ) {
                    items(tasks) { task ->
                        val isActive = task.id == activeTaskId

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isActive) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent)
                                .clickable { onTaskClick(task) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = task.isCompleted,
                                onCheckedChange = { onTaskToggleStatus(task) }
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

            // --- Add Task Input ---
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