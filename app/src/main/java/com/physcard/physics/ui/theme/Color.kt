package com.physcard.physics.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Palette derived from the launcher icon: a near-black ink base, a vivid azure accent
 * (#2871FE) and the warm off-white of the artwork's strokes.
 *
 * The accent is used as a *point* colour, not a flood fill: large surfaces stay paper /
 * ink, while the azure appears on actions, active states and data emphasis. A secondary
 * teal and a warm amber keep the UI from reading as one flat blue.
 */

// --- Light -------------------------------------------------------------------------
internal val Paper = Color(0xFFF7F8FB)          // cool paper, a hair cooler than before
internal val PaperElevated = Color(0xFFFFFFFF)
internal val Ink = Color(0xFF101218)
internal val InkSoft = Color(0xFF4A5060)

internal val Azure = Color(0xFF2871FE)          // icon accent
internal val AzureDeep = Color(0xFF1A4FC4)
internal val AzureSoft = Color(0xFFE4EDFF)      // tint for containers

internal val Teal = Color(0xFF0E8C8C)           // secondary, keeps blue from dominating
internal val TealSoft = Color(0xFFDDF2F1)

internal val Amber = Color(0xFFB87A2B)          // tertiary / warmth
internal val AmberSoft = Color(0xFFF8EAD6)

internal val Coral = Color(0xFFC0453C)
internal val Line = Color(0xFFE3E6EE)
internal val LineStrong = Color(0xFFC3C9D6)

// --- Dark --------------------------------------------------------------------------
internal val Night = Color(0xFF0D0D0E)          // exactly the icon background
internal val NightElevated = Color(0xFF16171A)
internal val NightSurface = Color(0xFF1E2025)
internal val Frost = Color(0xFFEDEFF5)
internal val FrostSoft = Color(0xFFA9B0C0)
internal val NightLine = Color(0xFF2A2D35)

internal val AzureBright = Color(0xFF6D9BFF)    // readable azure on dark
internal val AzureDim = Color(0xFF1B2B4D)
internal val TealBright = Color(0xFF4FC9C9)
internal val TealDim = Color(0xFF13312F)
internal val AmberBright = Color(0xFFE7B27A)
internal val AmberDim = Color(0xFF3D2E17)
internal val CoralBright = Color(0xFFFFB4A8)
