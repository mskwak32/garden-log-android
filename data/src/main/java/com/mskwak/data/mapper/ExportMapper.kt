package com.mskwak.data.mapper

import com.mskwak.domain.model.DiaryExport
import com.mskwak.domain.model.ExportRequest
import com.mskwak.domain.model.ExportedFileInfo
import com.mskwak.domain.model.PlantDetailExport
import com.mskwak.pdf.model.DiaryEntryPdf
import com.mskwak.pdf.model.DiaryPdf
import com.mskwak.pdf.model.PdfFileInfo
import com.mskwak.pdf.model.PdfRequest
import com.mskwak.pdf.model.PlantDetailPdf
import com.mskwak.pdf.model.TaskPdf

fun ExportRequest.toPdfRequest(): PdfRequest = PdfRequest(
    plantName = plantName,
    includeFirstPage = includeFirstPage,
    plantDetail = plantDetail?.toPlantDetailPdf(),
    diaries = diaries.map { it.toDiaryPdf() }
)

fun PlantDetailExport.toPlantDetailPdf(): PlantDetailPdf = PlantDetailPdf(
    name = name,
    imagePath = imagePath,
    plantingDate = plantingDate,
    wateringCycle = wateringCycle,
    memo = memo,
    harvestDate = harvestDate,
    harvestMemo = harvestMemo
)

fun DiaryExport.toDiaryPdf(): DiaryPdf = DiaryPdf(
    date = date,
    entries = entries.map { DiaryEntryPdf(content = it.content, imagePaths = it.imagePaths) },
    hasWateringRecord = hasWateringRecord,
    tasks = tasks.map { TaskPdf(title = it.title, memo = it.memo) }
)

fun PdfFileInfo.toExportedFileInfo(): ExportedFileInfo = ExportedFileInfo(
    contentUri = contentUri.toString(),
    fileName = fileName,
    createdAt = createdAt,
    fileSize = fileSize
)
