package com.lfcreative.lfscan.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.QrCodeScanner
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private data class ScannerTypeInfo(
    val key: String,
    val label: String,
    val icon: ImageVector
)

private val scannerTypes = listOf(
    ScannerTypeInfo("camera",   "Camera",           Icons.Default.CameraAlt),
    ScannerTypeInfo("external", "External Scanner", Icons.Default.QrCodeScanner),
    ScannerTypeInfo("internal", "Internal Scanner", Icons.Default.DocumentScanner),
    ScannerTypeInfo("manual",   "Manual",           Icons.Default.Keyboard)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScannerTypeSelectScreen(
    mode: String,
    onBack: () -> Unit,
    onScannerTypeSelected: (String) -> Unit
) {
    val modeAccent = modeColor(mode)

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        modeTitle(mode),
                        color = Color.White,
                        fontWeight = FontWeight.SemiBold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = modeAccent)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            scannerTypes.forEachIndexed { index, typeInfo ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .clickable { onScannerTypeSelected(typeInfo.key) },
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            typeInfo.icon,
                            contentDescription = typeInfo.label,
                            modifier = Modifier.size(48.dp),
                            tint = modeAccent
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(
                            typeInfo.label,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                if (index < scannerTypes.lastIndex) {
                    HorizontalDivider(color = Color(0xFFE5E7EB))
                }
            }
        }
    }
}
