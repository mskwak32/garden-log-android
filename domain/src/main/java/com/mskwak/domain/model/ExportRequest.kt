package com.mskwak.domain.model

import java.time.LocalDate

data class ExportRequest(
    val plantName: String,
    val includeFirstPage: Boolean,
    val plantDetail: PlantDetailExport?,
    val diaries: List<DiaryExport>
)

data class PlantDetailExport(
    val name: String,
    val imagePath: String?,
    val plantingDate: LocalDate?,
    val wateringCycle: Int,
    val memo: String?,
    val harvestDate: LocalDate? = null,
    val harvestMemo: String? = null
)

/**
 * 내보내기의 하루치 기록. entries·hasWateringRecord·tasks 중 최소 하나는 비어 있지 않다.
 */
data class DiaryExport(
    val date: LocalDate,
    val entries: List<DiaryEntryExport> = emptyList(),
    val hasWateringRecord: Boolean = false,
    val tasks: List<TaskExport> = emptyList()
)

data class DiaryEntryExport(
    val content: String,
    val imagePaths: List<String>
)

data class TaskExport(
    val title: String,
    val memo: String? = null
)
