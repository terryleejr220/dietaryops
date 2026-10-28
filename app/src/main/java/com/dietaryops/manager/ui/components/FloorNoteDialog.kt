package com.dietaryops.manager.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.util.ErrorLogger

@Composable
fun FloorNoteDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val context = LocalContext.current
    var noteContentInput by remember { mutableStateOf("") }
    var selectedNoteType by remember { mutableStateOf("💡 Feature Idea") }
    var isSubmitting by remember { mutableStateOf(false) }
    var isViewingLogs by remember { mutableStateOf(false) }

    val noteTypes = listOf(
        "💡 Feature Idea",
        "⚠️ Bug / Flaw",
        "🏷️ Label Formatting",
        "📱 UX / Interface"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Lightbulb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    if (isViewingLogs) "Submitted Floor Notes" else "Report Floor Note / Idea",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                TextButton(onClick = { isViewingLogs = !isViewingLogs }) {
                    Text(
                        if (isViewingLogs) "New Note" else "View Log",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            if (isViewingLogs) {
                val savedNotes = remember { ErrorLogger.getFloorNotes(context) }
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = savedNotes.ifBlank { "No submitted floor notes or bug logs found in local storage." },
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 240.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            } else {
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        "Submit ideas, bugs, or workflow feedback directly from the floor while testing. Notes sync to Google Sheets & Cloud logs.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // Note Type Selector
                    Text(
                        "Category:",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        noteTypes.forEach { type ->
                            FilterChip(
                                selected = selectedNoteType == type,
                                onClick = { selectedNoteType = type },
                                label = { Text(type, fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = noteContentInput,
                        onValueChange = { noteContentInput = it },
                        label = { Text("Note / Idea Details") },
                        placeholder = { Text("e.g., Prep sticker font for tuna salad needs to be larger...") },
                        singleLine = false,
                        maxLines = 4,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                    )

                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(8.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Text(
                                "Auto-Captured Context:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                "Staff: ${settingsManager.staffName} (${settingsManager.employeeId} - ${settingsManager.staffRole})",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                            Text(
                                "Facility: ${settingsManager.companyCode} \u2022 ${settingsManager.department}",
                                fontSize = 10.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (!isViewingLogs) {
                Button(
                    onClick = {
                        if (noteContentInput.isBlank()) {
                            Toast.makeText(context, "Please enter note details", Toast.LENGTH_SHORT)
                                .show()
                            return@Button
                        }
                        isSubmitting = true
                        ErrorLogger.logFloorNote(
                            context = context,
                            settingsManager = settingsManager,
                            noteType = selectedNoteType,
                            noteContent = noteContentInput.trim()
                        )
                        isSubmitting = false
                        Toast.makeText(
                            context,
                            "Note submitted! Synced to Google Sheets & Logs.",
                            Toast.LENGTH_SHORT
                        ).show()
                        onSubmitSuccess()
                    },
                    enabled = !isSubmitting,
                    shape = RoundedCornerShape(10.dp)
                ) {
                    if (isSubmitting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Submit Note")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
