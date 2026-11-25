package com.mskwak.pdf.model

import java.time.LocalDate

data class PdfRequest(
    val plantName: String,
    val includeFirstPage: Boolean,
    val plantDetail: PlantDetailPdf?,
    val diaries: List<DiaryPdf>
)

data class PlantDetailPdf(
    val name: String,
    val imagePath: String?,
    val plantingDate: LocalDate?,
    val wateringCycle: Int,
    val memo: String?,
    val harvestDate: LocalDate? = null,
    val harvestMemo: String? = null
)

/**
 * PDF의 하루치 블록. 날짜 줄·물주기·할일은 한 번, 그 아래 entries를 차례로 그린다.
 */
data class DiaryPdf(
    val date: LocalDate,
    val entries: List<DiaryEntryPdf> = emptyList(),
    val hasWateringRecord: Boolean = false,
    val tasks: List<TaskPdf> = emptyList()
)

data class DiaryEntryPdf(
    val content: String,
    val imagePaths: List<String>
)

data class TaskPdf(
    val title: String,
    val memo: String? = null
)
