package com.lfcreative.lfscan.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.lfcreative.lfscan.ui.theme.Blue
import com.lfcreative.lfscan.ui.theme.Green
import com.lfcreative.lfscan.ui.theme.Grey
import com.lfcreative.lfscan.ui.theme.Purple

private data class ModeInfo(
    val key: String,
    val title: String,
    val description: String,
    val color: Color
)

private val modes = listOf(
    ModeInfo("check_out", "Check Out", "Assign gear to yourself for a shoot", Blue),
    ModeInfo("check_in",  "Check In",  "Return gear back to the studio",      Green),
    ModeInfo("update",    "Update",    "Update where an asset is right now",  Purple),
    ModeInfo("inquiry",   "Inquiry",   "Look up any asset by scanning its code", Grey)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModeSelectScreen(
    onModeSelected: (String) -> Unit,
    onSignOut: () -> Unit,
    viewModel: ModeSelectViewModel = hiltViewModel()
) {
    val member by viewModel.currentMember.collectAsState(initial = null)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = member?.let { "Hello, ${it.name}" } ?: "LF Scan",
                        fontWeight = FontWeight.SemiBold
                    )
                },
                actions = {
                    IconButton(onClick = { viewModel.signOut(onSignOut) }) {
                        Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Sign out")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFFF9FAFB))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(Modifier.height(8.dp))
            Text(
                "Select a mode to start scanning",
                fontSize = 14.sp,
                color = Color(0xFF6B7280)
            )
            Spacer(Modifier.height(4.dp))

            // 2×2 grid
            val rows = modes.chunked(2)
            rows.forEach { rowModes ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    rowModes.forEach { mode ->
                        ModeCard(
                            mode = mode,
                            onClick = { onModeSelected(mode.key) },
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeCard(
    mode: ModeInfo,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .height(140.dp)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(mode.color.copy(alpha = 0.12f), RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(16.dp)
                        .background(mode.color, RoundedCornerShape(4.dp))
                )
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    mode.title,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = mode.color
                )
                Text(
                    mode.description,
                    fontSize = 12.sp,
                    color = Color(0xFF6B7280),
                    lineHeight = 16.sp
                )
            }
        }
    }
}
