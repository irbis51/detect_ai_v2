package com.malaria.screens

import androidx.compose.foundation.Image
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.malaria.components.FilterCheckbox
import com.malaria.data.AnalysisRecord
import com.malaria.repository.AnalysisRepository
import com.malaria.utils.CsvExporter
import java.awt.FileDialog
import java.awt.Frame
import java.io.File
import java.time.format.DateTimeFormatter
import org.jetbrains.skia.Image as SkiaImage

private val detailDateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")

@Composable
fun HistoryScreen(
    onBackClick: () -> Unit,
) {
    val analysisRepository = remember { AnalysisRepository() }
    var selectedFilter by remember { mutableStateOf<String?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var allAnalyses by remember { mutableStateOf<List<AnalysisRecord>>(emptyList()) }
    var selectedAnalysis by remember { mutableStateOf<AnalysisRecord?>(null) }
    var analysisToDelete by remember { mutableStateOf<AnalysisRecord?>(null) }
    var showClearHistoryDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }

    fun reloadAnalyses() {
        allAnalyses = analysisRepository.getAllAnalyses()
    }

    LaunchedEffect(Unit) {
        reloadAnalyses()
    }

    val filteredAnalyses = remember(allAnalyses, selectedFilter, searchQuery) {
        val normalizedSearch = searchQuery.trim().lowercase()
        allAnalyses.filter { analysis ->
            val matchesDiagnosis = when (selectedFilter) {
                "positive" -> analysis.diagnosis == "parasitized"
                "negative" -> analysis.diagnosis == "uninfected"
                else -> true
            }
            val matchesSearch = normalizedSearch.isEmpty() ||
                analysis.fileName.lowercase().contains(normalizedSearch)
            matchesDiagnosis && matchesSearch
        }
    }

    val openedAnalysis = selectedAnalysis
    if (openedAnalysis != null) {
        AnalysisDetailsScreen(
            record = openedAnalysis,
            onBackClick = { selectedAnalysis = null },
            onDeleteClick = { analysisToDelete = openedAnalysis }
        )
    } else {
        HistoryListScreen(
            allAnalyses = allAnalyses,
            filteredAnalyses = filteredAnalyses,
            selectedFilter = selectedFilter,
            searchQuery = searchQuery,
            statusMessage = statusMessage,
            onFilterChange = { selectedFilter = it },
            onSearchChange = { searchQuery = it },
            onRecordClick = { selectedAnalysis = it },
            onDeleteClick = { analysisToDelete = it },
            onClearHistoryClick = { showClearHistoryDialog = true },
            onExportClick = {
                val all = analysisRepository.getAllAnalyses()
                if (all.isEmpty()) {
                    statusMessage = "История пуста - нечего экспортировать"
                } else {
                    val dialog = FileDialog(null as Frame?, "Сохранить историю анализов", FileDialog.SAVE)
                    dialog.file = "malaria_history.csv"
                    dialog.isVisible = true
                    val name = dialog.file
                    val dir = dialog.directory
                    dialog.dispose()

                    if (name != null && dir != null) {
                        val target = File(dir, if (name.endsWith(".csv")) name else "$name.csv")
                        val result = CsvExporter.export(all, target)
                        statusMessage = if (result.isSuccess) {
                            "Экспортировано: ${result.getOrNull()} записей -> ${target.name}"
                        } else {
                            "Ошибка экспорта: ${result.exceptionOrNull()?.message}"
                        }
                    }
                }
            },
            onBackClick = onBackClick
        )
    }

    analysisToDelete?.let { record ->
        AlertDialog(
            onDismissRequest = { analysisToDelete = null },
            title = { Text("Удалить запись?") },
            text = { Text("Запись \"${record.fileName}\" будет удалена из истории.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                    onClick = {
                        val deleted = analysisRepository.deleteById(record.id)
                        analysisToDelete = null
                        if (deleted) {
                            if (selectedAnalysis?.id == record.id) {
                                selectedAnalysis = null
                            }
                            reloadAnalyses()
                            statusMessage = "Запись удалена"
                        } else {
                            statusMessage = "Не удалось удалить запись"
                        }
                    }
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { analysisToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (showClearHistoryDialog) {
        AlertDialog(
            onDismissRequest = { showClearHistoryDialog = false },
            title = { Text("Очистить историю?") },
            text = { Text("Будут удалены все записи анализов. Изображения на диске не удаляются.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                    onClick = {
                        val deletedCount = analysisRepository.deleteAll()
                        showClearHistoryDialog = false
                        selectedAnalysis = null
                        reloadAnalyses()
                        statusMessage = "Удалено записей: $deletedCount"
                    }
                ) {
                    Text("Очистить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearHistoryDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
private fun HistoryListScreen(
    allAnalyses: List<AnalysisRecord>,
    filteredAnalyses: List<AnalysisRecord>,
    selectedFilter: String?,
    searchQuery: String,
    statusMessage: String?,
    onFilterChange: (String?) -> Unit,
    onSearchChange: (String) -> Unit,
    onRecordClick: (AnalysisRecord) -> Unit,
    onDeleteClick: (AnalysisRecord) -> Unit,
    onClearHistoryClick: () -> Unit,
    onExportClick: () -> Unit,
    onBackClick: () -> Unit
) {
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

        HistoryStats(allAnalyses)

        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchChange,
            label = { Text("Поиск по имени файла") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
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
                text = "Все",
                isSelected = selectedFilter == null,
                onClick = { onFilterChange(null) }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterCheckbox(
                text = "Заражено",
                isSelected = selectedFilter == "positive",
                onClick = { onFilterChange("positive") }
            )
            Spacer(modifier = Modifier.width(12.dp))
            FilterCheckbox(
                text = "Не заражено",
                isSelected = selectedFilter == "negative",
                onClick = { onFilterChange("negative") }
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = onExportClick) {
                Text("Экспорт в CSV")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                enabled = allAnalyses.isNotEmpty(),
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                onClick = onClearHistoryClick
            ) {
                Text("Очистить историю", color = Color.White)
            }
        }

        statusMessage?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = it, color = Color.White, fontSize = 13.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (filteredAnalyses.isEmpty()) {
            val emptyText = if (allAnalyses.isEmpty()) {
                "История анализов пуста"
            } else {
                "По выбранным фильтрам записей нет"
            }
            Text(
                text = emptyText,
                fontSize = 16.sp,
                color = Color.LightGray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center
            )
        } else {
            LazyColumn(modifier = Modifier.weight(1f)) {
                items(filteredAnalyses, key = { it.id }) { analysis ->
                    AnalysisHistoryItem(
                        record = analysis,
                        onClick = { onRecordClick(analysis) },
                        onDeleteClick = { onDeleteClick(analysis) }
                    )
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
private fun HistoryStats(analyses: List<AnalysisRecord>) {
    val total = analyses.size
    val infected = analyses.count { it.diagnosis == "parasitized" }
    val uninfected = analyses.count { it.diagnosis == "uninfected" }
    val averageConfidence = if (analyses.isEmpty()) {
        0.0
    } else {
        analyses.map { it.confidence }.average() * 100
    }

    Text(
        text = "Всего записей: $total | Заражено: $infected | Не заражено: $uninfected | Средняя уверенность: ${"%.1f".format(averageConfidence)}%",
        fontSize = 14.sp,
        color = Color.White,
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF7A7A7A))
            .padding(12.dp)
    )
}

@Composable
private fun AnalysisHistoryItem(
    record: AnalysisRecord,
    onClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val diagnosisColor = when (record.diagnosis) {
        "parasitized" -> Color(0xFFFF5252)
        "uninfected" -> Color(0xFF4CAF50)
        else -> Color.Gray
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFF7A7A7A))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = record.fileName,
                    fontSize = 14.sp,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Вероятность: ${"%.1f".format(record.confidence * 100)}%",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = diagnosisText(record.diagnosis),
                    fontSize = 14.sp,
                    color = diagnosisColor
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = record.getFormattedDate(),
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                onClick = onDeleteClick
            ) {
                Text("Удалить", color = Color.White, fontSize = 12.sp)
            }
        }
    }
}

@Composable
private fun AnalysisDetailsScreen(
    record: AnalysisRecord,
    onBackClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF929292))
            .verticalScroll(rememberScrollState())
            .padding(32.dp)
    ) {
        Text(
            text = "Детали анализа",
            fontSize = 28.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        AnalysisImagePreview(record.imagePath)

        Spacer(modifier = Modifier.height(16.dp))

        DetailRow("Файл", record.fileName)
        DetailRow("Диагноз", diagnosisText(record.diagnosis))
        DetailRow("Вероятность", "${"%.1f".format(record.confidence * 100)}%")
        DetailRow("Дата и время", record.analysisDate.format(detailDateFormatter))
        DetailRow("Путь к файлу", record.imagePath)
        DetailRow("Время обработки", "${"%.2f".format(record.processingTime)} сек")
        DetailRow("Модель", record.modelUsed)

        Spacer(modifier = Modifier.height(24.dp))

        Row {
            Button(onClick = onBackClick) {
                Text("← К истории")
            }

            Spacer(modifier = Modifier.width(12.dp))

            Button(
                colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F)),
                onClick = onDeleteClick
            ) {
                Text("Удалить эту запись", color = Color.White)
            }
        }
    }
}

@Composable
private fun AnalysisImagePreview(imagePath: String) {
    val imageBitmap = remember(imagePath) {
        runCatching {
            val file = File(imagePath)
            if (file.exists()) {
                SkiaImage.makeFromEncoded(file.readBytes()).toComposeImageBitmap()
            } else {
                null
            }
        }.getOrNull()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(260.dp)
            .background(Color(0xFF7A7A7A)),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "Изображение анализа",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Text(
                text = "Изображение не найдено",
                color = Color.LightGray,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp)
    ) {
        Text(text = label, fontSize = 12.sp, color = Color.LightGray)
        Text(text = value, fontSize = 15.sp, color = Color.White)
    }
}

private fun diagnosisText(diagnosis: String): String {
    return when (diagnosis) {
        "parasitized" -> "Заражено"
        "uninfected" -> "Не заражено"
        else -> diagnosis
    }
}
