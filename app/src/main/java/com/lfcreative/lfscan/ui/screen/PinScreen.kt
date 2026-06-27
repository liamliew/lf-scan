package com.lfcreative.lfscan.ui.screen

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

@Composable
fun PinScreen(
    onSuccess: () -> Unit,
    viewModel: PinViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val shakeOffset = remember { Animatable(0f) }

    LaunchedEffect(uiState) {
        when (uiState) {
            is PinUiState.Success -> onSuccess()
            is PinUiState.InvalidPin, is PinUiState.Error -> {
                repeat(4) {
                    shakeOffset.animateTo(14f, animationSpec = tween(50))
                    shakeOffset.animateTo(-14f, animationSpec = tween(50))
                }
                shakeOffset.animateTo(0f, animationSpec = tween(50))
                pin = ""
                showError = true
                delay(2000)
                showError = false
                viewModel.resetState()
            }
            else -> {}
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White),
        contentAlignment = Alignment.Center
    ) {
        if (uiState is PinUiState.Loading) {
            CircularProgressIndicator()
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(32.dp),
                modifier = Modifier.padding(24.dp)
            ) {
                Text("LF Scan", fontSize = 28.sp, fontWeight = FontWeight.Bold)

                // 4-cell PIN display
                Row(
                    modifier = Modifier.offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(52.dp)
                                .border(
                                    width = 2.dp,
                                    color = when {
                                        showError -> Color(0xFFEF4444)
                                        index < pin.length -> Color(0xFF374151)
                                        else -> Color(0xFFD1D5DB)
                                    },
                                    shape = RoundedCornerShape(10.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < pin.length) {
                                Box(
                                    modifier = Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF111827))
                                )
                            }
                        }
                    }
                }

                if (showError) {
                    Text(
                        text = if (uiState is PinUiState.Error)
                            (uiState as PinUiState.Error).message
                        else "Invalid PIN",
                        color = Color(0xFFEF4444),
                        fontSize = 14.sp
                    )
                } else {
                    Spacer(Modifier.height(20.dp))
                }

                PinNumpad(
                    onDigit = { digit -> if (pin.length < 4) pin += digit },
                    onBackspace = { if (pin.isNotEmpty()) pin = pin.dropLast(1) },
                    onConfirm = { if (pin.length == 4) viewModel.confirmPin(pin) }
                )
            }
        }
    }
}

@Composable
private fun PinNumpad(
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
        listOf("back", "0", "ok")
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { key ->
                    PinKey(key = key, onDigit = onDigit, onBackspace = onBackspace, onConfirm = onConfirm)
                }
            }
        }
    }
}

@Composable
private fun PinKey(
    key: String,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onConfirm: () -> Unit
) {
    val keySize = 72.dp
    when (key) {
        "back" -> FilledTonalIconButton(onClick = onBackspace, modifier = Modifier.size(keySize)) {
            Icon(Icons.Default.Backspace, contentDescription = "Delete")
        }
        "ok" -> FilledIconButton(onClick = onConfirm, modifier = Modifier.size(keySize)) {
            Icon(Icons.Default.Check, contentDescription = "Confirm")
        }
        else -> FilledTonalButton(
            onClick = { onDigit(key) },
            modifier = Modifier.size(keySize),
            shape = RoundedCornerShape(12.dp),
            contentPadding = PaddingValues(0.dp)
        ) {
            Text(key, fontSize = 22.sp, fontWeight = FontWeight.Medium)
        }
    }
}
