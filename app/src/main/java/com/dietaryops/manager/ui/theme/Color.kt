package com.dietaryops.manager.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Light Theme Tokens (Crisp Clean Healthcare Slate, Rich Teal & Azure Accents)
val PrimaryLight = Color(0xFF008375)
val OnPrimaryLight = Color(0xFFFFFFFF)
val PrimaryContainerLight = Color(0xFFD3F8F2)
val OnPrimaryContainerLight = Color(0xFF00201B)

val SecondaryLight = Color(0xFF007799)
val OnSecondaryLight = Color(0xFFFFFFFF)
val SecondaryContainerLight = Color(0xFFD6F3FF)
val OnSecondaryContainerLight = Color(0xFF001F29)

val TertiaryLight = Color(0xFF334155) // Slate
val OnTertiaryLight = Color(0xFFFFFFFF)
val TertiaryContainerLight = Color(0xFFE2E8F0)
val OnTertiaryContainerLight = Color(0xFF0F172A)

val BackgroundLight = Color(0xFFF8FAFC) // Soft Slate White
val OnBackgroundLight = Color(0xFF0F172A)
val SurfaceLight = Color(0xFFFFFFFF)
val OnSurfaceLight = Color(0xFF0F172A)
val SurfaceVariantLight = Color(0xFFF1F5F9)
val OnSurfaceVariantLight = Color(0xFF475569)
val OutlineLight = Color(0xFFCBD5E1)
val OutlineVariantLight = Color(0xFFE2E8F0)

// Dark Theme Tokens (Deep Midnight Slate #0A0F1D, High-Tech Cyan #00E5FF & Emerald #00E676)
val PrimaryDark = Color(0xFF00E5FF) // Electric Cyan
val OnPrimaryDark = Color(0xFF061520)
val PrimaryContainerDark = Color(0xFF004D56)
val OnPrimaryContainerDark = Color(0xFF80F5FF)

val SecondaryDark = Color(0xFF00E676) // Vivid Mint Green
val OnSecondaryDark = Color(0xFF00220E)
val SecondaryContainerDark = Color(0xFF005324)
val OnSecondaryContainerDark = Color(0xFF8CFEBF)

val TertiaryDark = Color(0xFF94A3B8)
val OnTertiaryDark = Color(0xFF0A0F1D)
val TertiaryContainerDark = Color(0xFF1E293B)
val OnTertiaryContainerDark = Color(0xFFE2E8F0)

val BackgroundDark = Color(0xFF090D1A) // Deep Tech Slate
val OnBackgroundDark = Color(0xFFF1F5F9)
val SurfaceDark = Color(0xFF111827) // Elevated Surface
val OnSurfaceDark = Color(0xFFF8FAFC)
val SurfaceVariantDark = Color(0xFF1B243B)
val OnSurfaceVariantDark = Color(0xFF94A3B8)
val OutlineDark = Color(0xFF283550)
val OutlineVariantDark = Color(0xFF1E293B)

// Operational & Freshness Status Colors
val SyncSuccessColor = Color(0xFF10B981)
val SyncPendingColor = Color(0xFFF59E0B)
val StatusErrorColor = Color(0xFFEF4444)
val FreshGreen = Color(0xFF10B981)
val ExpiringSoonAmber = Color(0xFFF59E0B)
val ExpiredRed = Color(0xFFEF4444)

// Category Accent Colors
val CoolerBlue = Color(0xFF38BDF8)
val FreezerCyan = Color(0xFF22D3EE)
val DryStorageAmber = Color(0xFFFBBF24)
val ProduceGreen = Color(0xFF34D399)

// Modern Gradients
val PrimaryGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00C9A7), Color(0xFF00B4D8))
)

val ActionButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF00B4D8), Color(0xFF0077B6))
)

val SyncButtonGradient = Brush.horizontalGradient(
    colors = listOf(Color(0xFF10B981), Color(0xFF059669))
)

val ScannerLaserBrush = Brush.verticalGradient(
    colors = listOf(
        Color(0x0000E5FF),
        Color(0x8000E5FF),
        Color(0xFF00E5FF),
        Color(0x8000E5FF),
        Color(0x0000E5FF)
    )
)

val HeaderCardGradient = Brush.verticalGradient(
    colors = listOf(
        Color(0xFF151E34),
        Color(0xFF0E1526)
    )
)
