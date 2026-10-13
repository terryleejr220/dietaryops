package com.dietaryops.manager.ui.components

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Thermostat
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.StationType
import com.dietaryops.manager.util.ErrorLogger

@Composable
fun HaccpTempDialog(
    settingsManager: SettingsManager,
    onDismiss: () -> Unit,
    onSubmitSuccess: () -> Unit
) {
    val context = LocalContext.current
    var selectedStation by remember { mutableStateOf(StationType.STEAM_TABLE_HOT) }
    var itemNameInput by remember { mutableStateOf("") }
    var tempInputStr by remember { mutableStateOf("") }
    var isSubmitting by remember { mutableStateOf(false) }

    val tempDouble = tempInputStr.toDoubleOrNull()
    val isEvaluated = tempDouble != null
    val isPass = tempDouble != null && selectedStation.evaluateStatus(tempDouble)

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(if (isEvaluated && !isPass) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Thermostat,
                    contentDescription = null,
                    tint = if (isEvaluated && !isPass) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(24.dp)
                )
            }
        },
        title = { Text("Log HACCP Temperature", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    "Record pre-service steam table, cold well, and trayline cart temperatures to enforce ServSafe food safety standards.",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Station Type Selector Chips
                Text("Station / Location:", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    StationType.entries.forEach { station ->
                        FilterChip(
                            selected = selectedStation == station,
                            onClick = { selectedStation = station },
                            label = { Text(station.displayName, fontSize = 11.sp) }
                        )
                    }
                }

                OutlinedTextField(
                    value = itemNameInput,
                    onValueChange = { itemNameInput = it },
                    label = { Text("Food Item / Well Name (Optional)") },
                    placeholder = { Text("e.g. Roast Pork Loin / Soup Well #1") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = tempInputStr,
                    onValueChange = { tempInputStr = it },
                    label = { Text("Recorded Temperature (°F)") },
                    placeholder = { Text("e.g. 152 or 38") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                // Pass / Fail Status Badge
                Surface(
                    color = when {
                        !isEvaluated -> MaterialTheme.colorScheme.surfaceVariant
                        isPass -> Color(0xFF10B981).copy(alpha = 0.15f)
                        else -> MaterialTheme.colorScheme.errorContainer
                    },
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "HACCP Target Standard:",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = when (selectedStation) {
                                    StationType.STEAM_TABLE_HOT, StationType.TRAYLINE_CART -> "Hot Holding: ≥ 140°F"
                                    StationType.COLD_WELL, StationType.WALK_IN_COOLER -> "Cold Holding: ≤ 41°F"
                                    else -> "Freezer: ≤ 0°F"
                                },
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }

                        if (isEvaluated) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = if (isPass) Icons.Default.CheckCircle else Icons.Default.Warning,
                                    contentDescription = null,
                                    tint = if (isPass) Color(0xFF10B981) else MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = if (isPass) "PASS" else "ACTION REQ",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isPass) Color(0xFF10B981) else MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (tempDouble == null) {
                        Toast.makeText(context, "Please enter a valid temperature", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    isSubmitting = true
                    val stationName = itemNameInput.ifBlank { selectedStation.displayName }
                    val statusText = if (isPass) "PASS" else "FAIL (Action Required)"
                    val logContent = "HACCP TEMP LOG: $stationName = ${tempDouble}°F [$statusText]"

                    ErrorLogger.logFloorNote(
                        context = context,
                        settingsManager = settingsManager,
                        noteType = "🌡️ HACCP Temp",
                        noteContent = logContent
                    )
                    isSubmitting = false
                    Toast.makeText(context, "Logged ${tempDouble}°F for $stationName [$statusText]", Toast.LENGTH_SHORT).show()
                    onSubmitSuccess()
                },
                enabled = !isSubmitting,
                shape = RoundedCornerShape(10.dp)
            ) {
                Text("Log Temperature")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
