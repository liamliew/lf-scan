package com.lfcreative.lfscan.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfcreative.lfscan.ui.theme.Green

@Composable
fun CommitResultScreen(
    mode: String,
    count: Int,
    viewModel: ScanViewModel,
    onBackToMenu: () -> Unit
) {
    // Disable system back on this screen
    BackHandler(enabled = true) {}

    val state by viewModel.state.collectAsState()
    val scale = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        scale.animateTo(
            targetValue = 1f,
            animationSpec = spring(
                dampingRatio = Spring.DampingRatioMediumBouncy,
                stiffness = Spring.StiffnessMedium
            )
        )
    }

    val actionLabel = when (mode) {
        "check_out"  -> "Checked Out"
        "check_in"   -> "Checked In"
        "update"     -> "Updated"
        "mark_lost"  -> "Marked Lost"
        "rent_out"   -> "Rented Out"
        else         -> "Looked Up"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(48.dp))

        // Animated checkmark
        Box(
            modifier = Modifier
                .size(88.dp)
                .scale(scale.value)
                .clip(CircleShape)
                .background(Green),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(52.dp)
            )
        }

        Spacer(Modifier.height(24.dp))

        Text("Done", fontSize = 28.sp, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(6.dp))

        Text(
            "$count item${if (count != 1) "s" else ""} processed",
            fontSize = 15.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            // ── Committed (known) items ──────────────────────────────────────
            items(state.committedItems) { item ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        item.container?.container?.name ?: item.asset?.name ?: item.rawCode,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.weight(1f),
                        maxLines = 1
                    )
                    Text(
                        actionLabel,
                        fontSize = 13.sp,
                        color = Green,
                        fontWeight = FontWeight.Medium
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            }

            // ── Skipped (unknown) items ──────────────────────────────────────
            if (state.skippedItems.isNotEmpty()) {
                item {
                    Text(
                        "Skipped (not found)",
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 20.dp, bottom = 6.dp)
                    )
                }
                items(state.skippedItems) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            item.rawCode,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            "Not found",
                            fontSize = 13.sp,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Medium
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = {
                viewModel.resetSession()
                onBackToMenu()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back to Menu", fontWeight = FontWeight.SemiBold)
        }
    }
}
