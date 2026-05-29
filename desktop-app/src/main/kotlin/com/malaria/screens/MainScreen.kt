package com.malaria.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.Brush
import com.malaria.components.MalariaApiClient
import kotlinx.coroutines.delay

@Composable
fun MainScreen(
    onAnalyzeClick: () -> Unit,
    onHistoryClick: () -> Unit,
    onAboutClick: () -> Unit
) {
    var isServerOnline by remember { mutableStateOf<Boolean?>(null) }

    LaunchedEffect(Unit) {
        while (true) {
            isServerOnline = MalariaApiClient.isServerHealthy()
            delay(10_000)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(brush = Brush.verticalGradient(
                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
            ))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        ServerStatusIndicator(isServerOnline)

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "🩸 AI Malaria Detection 🦠",
            fontSize = 40.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(60.dp))

        Button(
            onClick = onAnalyzeClick,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(60.dp)
        ) {
            Text("Анализировать изображение", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onHistoryClick,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(60.dp)
        ) {
            Text("История анализов", color = Color.White)
        }

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAboutClick,
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(60.dp)
        ) {
            Text("О приложении", color = Color.White)
        }
    }
}

@Composable
private fun ServerStatusIndicator(isServerOnline: Boolean?) {
    val statusColor = when (isServerOnline) {
        true -> Color(0xFF4CAF50)
        false -> Color(0xFFFF5252)
        null -> Color(0xFFFFCC02)
    }
    val statusText = when (isServerOnline) {
        true -> "Сервер: онлайн"
        false -> "Сервер: офлайн"
        null -> "Сервер: проверка"
    }

    Row(
        modifier = Modifier
            .background(Color(0x66000000))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(statusColor)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(statusText, color = Color.White, fontSize = 14.sp)
    }
}
