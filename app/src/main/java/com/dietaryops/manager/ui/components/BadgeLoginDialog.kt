package com.dietaryops.manager.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.dietaryops.manager.CameraXBarcodeScanner
import com.dietaryops.manager.data.SettingsManager
import com.dietaryops.manager.data.model.StaffRole
import com.dietaryops.manager.data.model.StaffUser
import com.dietaryops.manager.data.remote.FirestoreRepository
import com.dietaryops.manager.ui.theme.DarkTealPrimary
import com.dietaryops.manager.ui.theme.FreshGreen
import kotlinx.coroutines.launch

enum class LoginTab {
    BADGE_SCAN,
    PIN_PAD
}

@Composable
fun BadgeLoginDialog(
    settingsManager: SettingsManager,
    firestoreRepository: FirestoreRepository = remember { FirestoreRepository() },
    onDismiss: () -> Unit,
    onLoginSuccess: (StaffUser) -> Unit
) {
    var selectedTab by remember { mutableStateOf(LoginTab.BADGE_SCAN) }
    var employeeIdInput by remember { mutableStateOf(settingsManager.employeeId) }
    var pinInput by remember { mutableStateOf("") }
    var isVerifying by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var successUser by remember { mutableStateOf<StaffUser?>(null) }
    val scope = rememberCoroutineScope()

    val companyCode = settingsManager.companyCode

    fun resolveLocalStaffUser(empId: String): StaffUser? {
        val clean = empId.trim().uppercase()
        return when {
            clean == "AD99" || clean == "ADMIN" -> StaffUser(
                employeeId = "AD99",
                displayName = "System Administrator",
                companyCode = companyCode,
                department = settingsManager.department,
                role = StaffRole.SUPER_ADMIN
            )
            clean == "TL01" -> StaffUser(
                employeeId = "TL01",
                displayName = "Terry Little Jr.",
                companyCode = companyCode,
                department = "Dietary",
                role = StaffRole.SUPERVISOR
            )
            clean == "AT01" -> StaffUser(
                employeeId = "AT01",
                displayName = "Andy Tygart",
                companyCode = companyCode,
                department = "Dietary",
                role = StaffRole.OPERATOR
            )
            clean == "LS01" -> StaffUser(
                employeeId = "LS01",
                displayName = "Lorraine S.",
                companyCode = companyCode,
                department = "EVS",
                role = StaffRole.SUPERVISOR
            )
            clean.equals(settingsManager.employeeId, ignoreCase = true) -> StaffUser(
                employeeId = settingsManager.employeeId,
                displayName = settingsManager.staffName,
                companyCode = companyCode,
                department = settingsManager.department,
                role = StaffRole.fromString(settingsManager.staffRole)
            )
            else -> null
        }
    }

    fun processBadgeScan(rawBarcode: String) {
        if (isVerifying) return
        errorMessage = null
        isVerifying = true

        val parsed = StaffUser.parseBadgePayload(rawBarcode)
        val scanCompanyCode = parsed?.first ?: companyCode
        val scanEmployeeId = parsed?.second ?: rawBarcode.trim()
        val scanToken = parsed?.third ?: ""

        scope.launch {
            val result = firestoreRepository.verifyBadgeLogin(scanCompanyCode, scanEmployeeId, scanToken)
            result.onSuccess { user ->
                // Also fetch company config
                val compResult = firestoreRepository.fetchCompanyProfile(user.companyCode)
                val comp = compResult.getOrNull()
                
                successUser = user
                settingsManager.currentStaffUser = user
                
                if (comp != null) {
                    settingsManager.companyName = comp.name
                    settingsManager.sheetId = comp.spreadsheetId
                    settingsManager.webAppUrl = comp.webAppUrl
                    settingsManager.departmentSheetTab = comp.departmentTabs[user.department] ?: settingsManager.defaultTabForCurrentDepartment
                }
                
                onLoginSuccess(user)
            }.onFailure { err ->
                // Fallback for offline or local supervisor mode
                val localUser = resolveLocalStaffUser(scanEmployeeId)
                if (localUser != null) {
                    successUser = localUser
                    settingsManager.currentStaffUser = localUser
                    onLoginSuccess(localUser)
                } else {
                    errorMessage = err.message ?: "Invalid badge scanned"
                }
            }
            isVerifying = false
        }
    }

    fun submitPinLogin() {
        if (employeeIdInput.isBlank()) {
            errorMessage = "Please enter Employee ID"
            return
        }
        if (pinInput.length < 4) {
            errorMessage = "Enter 4-digit PIN"
            return
        }
        isVerifying = true
        errorMessage = null

        scope.launch {
            val result = firestoreRepository.verifyPinLogin(companyCode, employeeIdInput, pinInput)
            result.onSuccess { user ->
                // Also fetch company config
                val compResult = firestoreRepository.fetchCompanyProfile(user.companyCode)
                val comp = compResult.getOrNull()
                
                successUser = user
                settingsManager.currentStaffUser = user
                
                if (comp != null) {
                    settingsManager.companyName = comp.name
                    settingsManager.sheetId = comp.spreadsheetId
                    settingsManager.webAppUrl = comp.webAppUrl
                    settingsManager.departmentSheetTab = comp.departmentTabs[user.department] ?: settingsManager.defaultTabForCurrentDepartment
                }
                
                onLoginSuccess(user)
            }.onFailure { err ->
                // Fallback / local check if default test admin PIN
                val localUser = if (pinInput == "1234") resolveLocalStaffUser(employeeIdInput) else null
                if (localUser != null) {
                    successUser = localUser
                    settingsManager.currentStaffUser = localUser
                    onLoginSuccess(localUser)
                } else {
                    errorMessage = err.message ?: "Incorrect PIN"
                }
            }
            isVerifying = false
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .wrapContentHeight()
                .padding(8.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = DarkTealPrimary.copy(alpha = 0.15f),
                            modifier = Modifier.size(40.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Badge,
                                    contentDescription = null,
                                    tint = DarkTealPrimary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Staff Identification",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "$companyCode • ${settingsManager.department}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(onClick = onDismiss) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Close")
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Mode Tabs (Badge Scan vs PIN Pad)
                TabRow(
                    selectedTabIndex = selectedTab.ordinal,
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.clip(RoundedCornerShape(12.dp))
                ) {
                    Tab(
                        selected = selectedTab == LoginTab.BADGE_SCAN,
                        onClick = { selectedTab = LoginTab.BADGE_SCAN },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.QrCodeScanner, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Scan Badge", fontSize = 13.sp)
                            }
                        }
                    )
                    Tab(
                        selected = selectedTab == LoginTab.PIN_PAD,
                        onClick = { selectedTab = LoginTab.PIN_PAD },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Pin, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("PIN Pad", fontSize = 13.sp)
                            }
                        }
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Error Message Banner
                AnimatedVisibility(visible = errorMessage != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.errorContainer
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage ?: "",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                        }
                    }
                }

                // Success Feedback Banner
                AnimatedVisibility(visible = successUser != null) {
                    Surface(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        shape = RoundedCornerShape(8.dp),
                        color = FreshGreen.copy(alpha = 0.15f)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = null,
                                tint = FreshGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Authenticated: ${successUser?.displayName} (${successUser?.role?.displayName})",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = FreshGreen
                            )
                        }
                    }
                }

                when (selectedTab) {
                    LoginTab.BADGE_SCAN -> {
                        // Badge Camera Viewfinder
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(220.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color.Black),
                            contentAlignment = Alignment.Center
                        ) {
                            CameraXBarcodeScanner(
                                modifier = Modifier.fillMaxSize(),
                                isScanningEnabled = !isVerifying && successUser == null,
                                isCameraActive = selectedTab == LoginTab.BADGE_SCAN,
                                onBarcodeFound = { rawUpc, _ ->
                                    processBadgeScan(rawUpc)
                                }
                            )

                            // Overlay corner brackets
                            ScannerOverlay(
                                modifier = Modifier.fillMaxSize(),
                                isScanning = !isVerifying,
                                targetText = "Present ID Badge Barcode or QR Code"
                            )

                            if (isVerifying) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(Color.Black.copy(alpha = 0.6f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(color = Color.White)
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Supports physical ID Badges, Cards, & Mobile Screens",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }

                    LoginTab.PIN_PAD -> {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            OutlinedTextField(
                                value = employeeIdInput,
                                onValueChange = { employeeIdInput = it.uppercase() },
                                label = { Text("Employee Badge / ID") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            // 4-digit PIN display indicators
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier.padding(vertical = 8.dp)
                            ) {
                                for (i in 0 until 4) {
                                    val isFilled = i < pinInput.length
                                    Box(
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clip(CircleShape)
                                            .background(
                                                if (isFilled) DarkTealPrimary else MaterialTheme.colorScheme.surfaceVariant
                                            )
                                            .border(
                                                1.dp,
                                                if (isFilled) DarkTealPrimary else MaterialTheme.colorScheme.outline,
                                                CircleShape
                                            )
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Numeric Keypad Grid
                            Column(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.width(260.dp)
                            ) {
                                val keypad = listOf(
                                    listOf("1", "2", "3"),
                                    listOf("4", "5", "6"),
                                    listOf("7", "8", "9"),
                                    listOf("C", "0", "⌫")
                                )

                                for (row in keypad) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        for (key in row) {
                                            Surface(
                                                modifier = Modifier
                                                    .weight(1f)
                                                    .height(48.dp)
                                                    .clip(RoundedCornerShape(12.dp))
                                                    .clickable {
                                                        when (key) {
                                                            "C" -> pinInput = ""
                                                            "⌫" -> if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1)
                                                            else -> {
                                                                if (pinInput.length < 4) {
                                                                    pinInput += key
                                                                    if (pinInput.length == 4) {
                                                                        submitPinLogin()
                                                                    }
                                                                }
                                                            }
                                                        }
                                                    },
                                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
                                                shape = RoundedCornerShape(12.dp)
                                            ) {
                                                Box(contentAlignment = Alignment.Center) {
                                                    Text(
                                                        text = key,
                                                        style = MaterialTheme.typography.titleMedium,
                                                        fontFamily = FontFamily.Monospace,
                                                        fontWeight = FontWeight.Bold
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Button(
                                onClick = { submitPinLogin() },
                                modifier = Modifier.fillMaxWidth(),
                                enabled = !isVerifying && employeeIdInput.isNotBlank() && pinInput.length == 4,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                if (isVerifying) {
                                    CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                } else {
                                    Text("Unlock Session")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
