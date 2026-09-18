package com.centuryvilla.deliveryscanner.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.centuryvilla.deliveryscanner.data.model.SyscoProduct

@Composable
fun PinDialog(
    onDismiss: () -> Unit,
    onConfirmPin: (String) -> Boolean
) {
    var pinInput by remember { mutableStateOf("") }
    var pinError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Manager Admin Login") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter your 4-digit manager PIN or scan your Admin Badge.", fontSize = 13.sp)
                OutlinedTextField(
                    value = pinInput,
                    onValueChange = {
                        if (it.length <= 8) {
                            pinInput = it
                            pinError = false
                        }
                    },
                    label = { Text("Manager PIN") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                    visualTransformation = PasswordVisualTransformation(),
                    isError = pinError,
                    modifier = Modifier.fillMaxWidth()
                )
                if (pinError) {
                    Text("Incorrect PIN. Please try again.", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                if (!onConfirmPin(pinInput)) {
                    pinError = true
                }
            }) {
                Text("Unlock")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun ChangePinDialog(
    onDismiss: () -> Unit,
    onSavePin: (String) -> Unit
) {
    var newPin by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change Manager PIN") },
        text = {
            OutlinedTextField(
                value = newPin,
                onValueChange = { if (it.length <= 8) newPin = it },
                label = { Text("New PIN") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(onClick = {
                if (newPin.isNotBlank()) {
                    onSavePin(newPin)
                }
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun WebhookDialog(
    currentWebhook: String,
    onDismiss: () -> Unit,
    onSaveWebhook: (String) -> Unit
) {
    var webhookInput by remember { mutableStateOf(currentWebhook) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Google Sheets Real-Time Sync") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Enter Google Apps Script Web App URL for CV inventory 226:", fontSize = 12.sp)
                OutlinedTextField(
                    value = webhookInput,
                    onValueChange = { webhookInput = it },
                    label = { Text("Webhook URL") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                onSaveWebhook(webhookInput)
            }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun EditProductDialog(
    scannedProduct: SyscoProduct,
    onDismiss: () -> Unit,
    onSaveProduct: (SyscoProduct) -> Unit
) {
    var editName by remember { mutableStateOf(scannedProduct.name) }
    var editDeliveryDays by remember { mutableStateOf(scannedProduct.shelfLifeDays.toString()) }
    var editOpenedDays by remember { mutableStateOf(scannedProduct.openedShelfLifeDays.toString()) }
    var editPackSize by remember { mutableStateOf(scannedProduct.packSize.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configure Product Rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("UPC: ${scannedProduct.upc}", fontSize = 12.sp, color = Color.Gray)
                OutlinedTextField(
                    value = editName,
                    onValueChange = { editName = it },
                    label = { Text("Product Name") },
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Shelf Life Presets (Default: 3 to 14 days):", fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    FilterChip(
                        selected = editDeliveryDays == "5",
                        onClick = { editDeliveryDays = "5"; editOpenedDays = "3" },
                        label = { Text("Meat/Produce (3-5d)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = editDeliveryDays == "10",
                        onClick = { editDeliveryDays = "10"; editOpenedDays = "7" },
                        label = { Text("Dairy/Eggs (5-14d)", fontSize = 10.sp) }
                    )
                    FilterChip(
                        selected = editDeliveryDays == "365",
                        onClick = { editDeliveryDays = "365"; editOpenedDays = "5" },
                        label = { Text("Canned/Dry (365d)", fontSize = 10.sp) }
                    )
                }

                OutlinedTextField(
                    value = editDeliveryDays,
                    onValueChange = { editDeliveryDays = it },
                    label = { Text("Unopened Shelf Life (Days)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editOpenedDays,
                    onValueChange = { editOpenedDays = it },
                    label = { Text("Opened TCS Shelf Life (Days, max 7)") },
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = editPackSize,
                    onValueChange = { editPackSize = it },
                    label = { Text("Pack Size (Units/Cs)") },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(onClick = {
                val dDays = editDeliveryDays.toIntOrNull() ?: 7
                val oDays = editOpenedDays.toIntOrNull() ?: 7
                val pSize = editPackSize.toIntOrNull() ?: 1
                val updated = SyscoProduct(scannedProduct.upc, editName, dDays, oDays, pSize)
                onSaveProduct(updated)
            }) {
                Text("Save Rule")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
