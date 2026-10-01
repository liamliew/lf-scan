package com.lfcreative.lfscan.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import com.lfcreative.lfscan.ui.theme.LocalExtendedColors
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lfcreative.lfscan.ui.theme.Green
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
        "check_in", "check_in_repeat" -> "Checked In"
        "update"     -> "Updated"
        "mark_lost"  -> "Marked Lost"
        else         -> "Looked Up"
    }

    val itemWord = if (count != 1) "items" else "item"
    val summary = when (mode) {
        "check_in" -> state.committedLocationName
            ?.let { "$count $itemWord checked in to $it" }
            ?: "$count $itemWord checked in"
        "check_in_repeat" -> "$count $itemWord checked in"
        "check_out" -> state.expectedReturnDateMillis
            ?.let { "$count $itemWord checked out, due back ${formatSummaryDate(it)}" }
            ?: "$count $itemWord checked out"
        else -> "$count $itemWord processed"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        // Heavy full-width flat color banner — same industrial style as StatusBanner
        // (AssetDetailScreen/ContainerDetailScreen): solid fill, bold oversized text, no rounding.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Green)
                .padding(vertical = 32.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                Icons.Default.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier
                    .size(64.dp)
                    .scale(scale.value)
            )
            Spacer(Modifier.height(16.dp))
            Text("DONE", fontSize = 30.sp, fontWeight = FontWeight.Black, color = Color.White, letterSpacing = 1.sp)
            Spacer(Modifier.height(6.dp))
            Text(
                summary.uppercase(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                textAlign = TextAlign.Center
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
        ) {
            Spacer(Modifier.height(24.dp))

            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // ── Committed (known) items ──────────────────────────────────
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
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.weight(1f),
                            maxLines = 1
                        )
                        Text(
                            actionLabel.uppercase(),
                            fontSize = 12.sp,
                            color = Green,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp
                        )
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                }

                // ── Skipped (unknown) items ──────────────────────────────────
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
                                fontFamily = com.lfcreative.lfscan.ui.theme.AppMonospaceFontFamily,
                                fontSize = 14.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                modifier = Modifier.weight(1f),
                                maxLines = 1
                            )
                            Text(
                                "NOT FOUND",
                                fontSize = 12.sp,
                                color = LocalExtendedColors.current.red,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                Text("Back to Menu", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

private fun formatSummaryDate(millis: Long): String =
    SimpleDateFormat("d MMM", Locale.getDefault()).format(Date(millis))
