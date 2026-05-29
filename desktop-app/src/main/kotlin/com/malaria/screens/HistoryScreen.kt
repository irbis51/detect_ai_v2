package com.malaria.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malaria.components.FilterCheckbox
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import com.malaria.data.AnalysisRecord
import com.malaria.repository.AnalysisRepository
import com.malaria.utils.CsvExporter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File

/**
 * Экран истории анализов.
 *
 * Функции:
 * - Отображение всех записей из БД (с расшифровкой через AnalysisRepository)
 * - Фильтрация по диагнозу (в памяти после загрузки)
 * - Экспорт всей истории в CSV через системный диалог сохранения файла
 */
@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
) {
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var analyses by remember { mutableStateOf<List<AnalysisRecord>>(emptyList()) }
    var exportMessage by remember { mutableStateOf<String?>(null) }
    val analysisRepository = remember { AnalysisRepository() }

    // Загружаем данные при открытии экрана и при изменении фильтра
    LaunchedEffect(selectedFilter) {
        analyses = if (selectedFilter != null) {
            val allAnalyses = analysisRepository.getAllAnalyses()
            allAnalyses.filter { analysis ->
                when (selectedFilter) {
                    "positive" -> analysis.diagnosis == "parasitized"
                    "negative" -> analysis.diagnosis == "uninfected"
                    else -> true
                }
            }
        } else {
            analysisRepository.getAllAnalyses()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF929292))
            .padding(32.dp)
    ) {
        Text(
            text = "История анализов",
            fontSize = 28.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Фильтр по результатам:",
            fontSize = 16.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            FilterCheckbox(
                label = "Все",
                checked = selectedFilter == null,
                onCheckedChange = { selectedFilter = null }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterCheckbox(
                label = "Заражено",
                checked = selectedFilter == "positive",
                onCheckedChange = { selectedFilter = "positive" }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterCheckbox(
                label = "Не заражено",
                checked = selectedFilter == "negative",
                onCheckedChange = { selectedFilter = "negative" }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Кнопка экспорта CSV
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = {
                val all = analysisRepository.getAllAnalyses()
                if (all.isEmpty()) {
                    exportMessage = "История пуста — нечего экспортировать"
                    return@Button
                }
                val dialog = FileDialog(null as Frame?, "Сохранить историю анализов", FileDialog.SAVE)
                dialog.file = "malaria_history.csv"
                dialog.isVisible = true
                val name = dialog.file
                val dir = dialog.directory
                dialog.dispose()
                if (name != null && dir != null) {
                    val target = File(dir, if (name.endsWith(".csv")) name else "$name.csv")
                    val result = CsvExporter.export(all, target)
                    exportMessage = if (result.isSuccess)
                        "✅ Экспортировано: ${result.getOrNull()} записей → ${target.name}"
                    else
                        "❌ Ошибка: ${result.exceptionOrNull()?.message}"
                }
            }) {
                Text("📥 Экспорт в CSV")
            }

            Spacer(modifier = Modifier.width(12.dp))

            exportMessage?.let {
                Text(text = it, color = Color.White, fontSize = 13.sp)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (analyses.isEmpty()) {
            Text(
                text = "История анализов пуста",
                fontSize = 16.sp,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(analyses) { analysis ->
                    AnalysisHistoryItem(analysis)
                    Spacer(modifier = Modifier.height(8.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = onBackClick) {
            Text("← Назад")
        }
    }
}

@Composable
fun AnalysisHistoryItem(record: AnalysisRecord) {
    val diagnosisColor = when (record.diagnosis) {
        "parasitized" -> Color(0xFFFF5252)
        "uninfected" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }
    val diagnosisText = when (record.diagnosis) {
        "parasitized" -> "Заражено"
        "uninfected" -> "Не заражено"
        else -> record.diagnosis
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF7A7A7A))
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = record.fileName,
                fontSize = 14.sp,
                color = Color.White
            )
            Text(
                text = diagnosisText,
                fontSize = 14.sp,
                color = diagnosisColor
            )
        }
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "Вероятность: ${"%.1f".format(record.confidence * 100)}%",
                fontSize = 12.sp,
                color = Color.LightGray
            )
            Text(
                text = record.analysisDate.toString().substringBefore("T").let { date ->
                    val time = record.analysisDate.toString().substringAfter("T").substringBefore(".")
                    "$date $time"
                },
                fontSize = 12.sp,
                color = Color.LightGray
            )
        }
    }
}
