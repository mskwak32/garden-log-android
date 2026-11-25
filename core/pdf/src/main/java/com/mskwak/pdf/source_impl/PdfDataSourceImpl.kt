package com.mskwak.pdf.source_impl

import android.content.ContentValues
import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.provider.MediaStore
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import android.text.TextUtils
import com.mskwak.pdf.PdfDataSource
import com.mskwak.pdf.R
import com.mskwak.pdf.model.DiaryEntryPdf
import com.mskwak.pdf.model.DiaryPdf
import com.mskwak.pdf.model.PdfFileInfo
import com.mskwak.pdf.model.PdfRequest
import com.mskwak.pdf.model.TaskPdf
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.format.DateTimeFormatter
import javax.inject.Inject

internal class PdfDataSourceImpl @Inject constructor(
    @param:ApplicationContext private val context: Context
) : PdfDataSource {

    companion object {
        private const val FOLDER_NAME = "GardenLog"
        private const val RELATIVE_PATH = "Download/$FOLDER_NAME/"
        private val DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd")

        // A4 @ 72 dpi (points)
        private const val PAGE_WIDTH = 595
        private const val PAGE_HEIGHT = 842

        // 여백 (px)
        private const val MARGIN = 56f

        // 본문 영역 너비
        private const val CONTENT_WIDTH = PAGE_WIDTH - MARGIN * 2

        // 이미지 2장 배치: 각 이미지 너비 (가로:세로 = 4:3)
        private const val IMAGE_GAP = 8f
        private const val IMAGE_WIDTH = (CONTENT_WIDTH - IMAGE_GAP) / 2f
        private const val IMAGE_HEIGHT = IMAGE_WIDTH * 3f / 4f

        // 식물 대표 이미지 (첫 페이지): 전체 너비, 동일 비율
        private const val PLANT_IMAGE_WIDTH = CONTENT_WIDTH
        private const val PLANT_IMAGE_HEIGHT = PLANT_IMAGE_WIDTH / 1.5f

        // 하루 블록 사이 간격 / 블록 내부 간격
        private const val DAY_SPACING = 26f
        private const val ENTRY_GAP = 10f
        private const val AFTER_BLOCK_GAP = 8f

        // 날짜 줄 아래 구분선: 글자 아래 간격, 구분선 아래 간격
        private const val DIVIDER_OFFSET = 7f
        private const val DIVIDER_AFTER_GAP = 10f

        // 긴 블록을 쪼갤 때 날짜와 함께 두는 최소 본문 줄 수
        private const val HEAD_TEXT_LINES = 3

        // 색상
        private const val GRAY_COLOR = "#9A9A9A"
        private const val VALUE_COLOR = "#333333"
        private const val DIVIDER_COLOR = "#E3E1DC"
        private const val CHIP_BG_COLOR = "#E7F1FB"
        private const val CHIP_TEXT_COLOR = "#3B8FD9"

        // 물주기 칩
        private const val CHIP_HEIGHT = 18f
        private const val CHIP_PADDING = 8f
        private const val CHIP_ICON_SIZE = 10f
        private const val CHIP_ICON_GAP = 3f
        private const val CHIP_TEXT_SIZE = 10f

        // 표지 라벨 열 너비
        private const val LABEL_COLUMN_WIDTH = 72f

        // 푸터: 하단 여백 안쪽에 그린다
        private const val FOOTER_TEXT_SIZE = 9f
        private const val FOOTER_BASELINE_OFFSET = 28f

        // 할일 글머리 원
        private const val TASK_DOT_COLOR = "#8D6E63"
        private const val TASK_DOT_RADIUS = 3f
        private const val TASK_DOT_GAP = 8f

        // 텍스트 크기
        private const val TITLE_TEXT_SIZE = 22f
        private const val LABEL_TEXT_SIZE = 13f
        private const val DATE_TEXT_SIZE = 14f
        private const val CONTENT_TEXT_SIZE = 12f

        // 줄 간격 추가값
        private const val LINE_SPACING_EXTRA = 4f

        // 이미지 품질 설정을 위한 DPI 스케일 (120 DPI 목표: 120 / 72 ≒ 1.666)
        private const val DPI_SCALE = 1.67f
    }

    override suspend fun generatePdf(request: PdfRequest): Uri = withContext(Dispatchers.IO) {
        val startDate = request.diaries.minOf { it.date }
        val endDate = request.diaries.maxOf { it.date }
        val fileName = "${request.plantName}_${startDate.format(DATE_FORMATTER)}_${
            endDate.format(DATE_FORMATTER)
        }.pdf"

        val contentValues = ContentValues().apply {
            put(MediaStore.Downloads.DISPLAY_NAME, fileName)
            put(MediaStore.Downloads.MIME_TYPE, "application/pdf")
            put(MediaStore.Downloads.RELATIVE_PATH, RELATIVE_PATH)
            put(MediaStore.Downloads.IS_PENDING, 1)
        }

        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
            ?: error("MediaStore insert 실패")

        try {
            val stream = resolver.openOutputStream(uri) ?: error("OutputStream 열기 실패")
            stream.use { outputStream ->
                // 1차: 총 페이지 수를 세기 위한 dry-run (푸터의 "현재 / 전체" 표기용)
                val dryDocument = PdfDocument()
                val totalPages = try {
                    PageWriter(dryDocument, request.plantName, dryRun = true, totalPages = 0)
                        .render(request)
                } finally {
                    dryDocument.close()
                }

                // 2차: 실제 렌더링
                val pdfDocument = PdfDocument()
                try {
                    PageWriter(pdfDocument, request.plantName, dryRun = false, totalPages = totalPages)
                        .render(request)
                    pdfDocument.writeTo(outputStream)
                } finally {
                    pdfDocument.close()
                }
            }

            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            resolver.update(uri, contentValues, null, null)
        } catch (e: Exception) {
            resolver.delete(uri, null, null)
            Timber.e(e, "PDF 생성 실패")
            throw e
        }

        Timber.d("PDF 생성 완료: $uri")
        uri
    }

    override suspend fun getExportedFiles(): List<PdfFileInfo> = withContext(Dispatchers.IO) {
        val files = mutableListOf<PdfFileInfo>()

        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.DATE_ADDED,
            MediaStore.Downloads.SIZE
        )
        val selection = "${MediaStore.Downloads.RELATIVE_PATH} = ?"
        val selectionArgs = arrayOf(RELATIVE_PATH)
        val sortOrder = "${MediaStore.Downloads.DATE_ADDED} DESC"

        context.contentResolver.query(
            MediaStore.Downloads.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
            val nameCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
            val dateCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.Downloads.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idCol)
                val uri =
                    Uri.withAppendedPath(MediaStore.Downloads.EXTERNAL_CONTENT_URI, id.toString())
                files.add(
                    PdfFileInfo(
                        contentUri = uri,
                        fileName = cursor.getString(nameCol),
                        createdAt = cursor.getLong(dateCol) * 1000L, // sec → ms
                        fileSize = cursor.getLong(sizeCol)
                    )
                )
            }
        }

        files
    }

    override suspend fun deleteFile(contentUri: Uri) = withContext(Dispatchers.IO) {
        val deleted = context.contentResolver.delete(contentUri, null, null)
        Timber.d("PDF 삭제: $contentUri, deleted=$deleted")
    }

    /**
     * PDF 페이지를 순차적으로 추가하는 내부 헬퍼.
     * 현재 페이지와 y 위치를 추적하며, 필요 시 새 페이지를 생성한다.
     *
     * dryRun이면 이미지 디코딩·푸터를 건너뛰고 레이아웃(y 진행)만 같게 수행해 총 페이지 수를 센다.
     */
    private inner class PageWriter(
        private val pdfDocument: PdfDocument,
        private val plantName: String,
        private val dryRun: Boolean,
        private val totalPages: Int
    ) {
        private var pageIndex = 1
        private var page: PdfDocument.Page = newPage()
        private var canvas: Canvas = page.canvas
        private var y: Float = MARGIN

        // 날짜 표기는 문자열 리소스와 같은 언어로 맞춘다
        private val locale = context.resources.configuration.locales[0]
        private val dateFormatter = DateTimeFormatter.ofPattern(
            context.getString(R.string.pdf_date_format),
            locale
        )
        private val weekdayFormatter = DateTimeFormatter.ofPattern("E", locale)

        private val datePaint = TextPaint().apply {
            color = Color.BLACK
            textSize = DATE_TEXT_SIZE
            isFakeBoldText = true
            isAntiAlias = true
        }
        private val weekdayPaint = TextPaint().apply {
            color = Color.parseColor(GRAY_COLOR)
            textSize = DATE_TEXT_SIZE
            isAntiAlias = true
        }
        private val contentPaint = TextPaint().apply {
            color = Color.DKGRAY
            textSize = CONTENT_TEXT_SIZE
            isAntiAlias = true
        }
        private val memoPaint = TextPaint(contentPaint).apply {
            color = Color.parseColor(GRAY_COLOR)
        }
        private val taskDotPaint = Paint().apply {
            color = Color.parseColor(TASK_DOT_COLOR)
            isAntiAlias = true
        }
        private val dividerPaint = Paint().apply {
            color = Color.parseColor(DIVIDER_COLOR)
        }
        private val chipPaint = Paint().apply {
            color = Color.parseColor(CHIP_BG_COLOR)
            isAntiAlias = true
        }
        private val chipTextPaint = TextPaint().apply {
            color = Color.parseColor(CHIP_TEXT_COLOR)
            textSize = CHIP_TEXT_SIZE
            isFakeBoldText = true
            isAntiAlias = true
        }
        private val footerPaint = TextPaint().apply {
            color = Color.parseColor(GRAY_COLOR)
            textSize = FOOTER_TEXT_SIZE
            isAntiAlias = true
        }
        private val waterDropDrawable = context.getDrawable(R.drawable.ic_water_drop_blue)

        private fun newPage(): PdfDocument.Page {
            val pageInfo =
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageIndex++).create()
            return pdfDocument.startPage(pageInfo)
        }

        /** 푸터(식물 이름 / 현재 / 전체)를 그리고 현재 페이지를 끝낸다 */
        private fun finishCurrentPage() {
            if (!dryRun) {
                val baseline = PAGE_HEIGHT - FOOTER_BASELINE_OFFSET
                canvas.drawText(plantName, MARGIN, baseline, footerPaint)
                val pageText = context.getString(
                    R.string.pdf_page_number_format,
                    pageIndex - 1,
                    totalPages
                )
                canvas.drawText(
                    pageText,
                    MARGIN + CONTENT_WIDTH - footerPaint.measureText(pageText),
                    baseline,
                    footerPaint
                )
            }
            pdfDocument.finishPage(page)
        }

        private fun finishCurrentAndStartNew() {
            finishCurrentPage()
            page = newPage()
            canvas = page.canvas
            y = MARGIN
        }

        /** 마지막 페이지를 끝내고 총 페이지 수를 반환한다 */
        private fun finish(): Int {
            finishCurrentPage()
            return pageIndex - 1
        }

        fun render(request: PdfRequest): Int {
            if (request.includeFirstPage && request.plantDetail != null) {
                addPlantDetailPage(request)
            }
            addDiaryPages(request.diaries)
            return finish()
        }

        /** 남은 페이지 높이 */
        private fun remainingHeight() = PAGE_HEIGHT - MARGIN - y

        private fun isPageEmpty() = y == MARGIN

        /** 필요 높이가 남은 공간보다 크면 새 페이지 */
        private fun ensureSpace(needed: Float) {
            if (remainingHeight() < needed) {
                finishCurrentAndStartNew()
            }
        }

        private fun addPlantDetailPage(request: PdfRequest) {
            val detail = request.plantDetail ?: return

            val titlePaint = TextPaint().apply {
                color = Color.BLACK
                textSize = TITLE_TEXT_SIZE
                isFakeBoldText = true
                isAntiAlias = true
            }
            val labelPaint = TextPaint().apply {
                color = Color.parseColor(GRAY_COLOR)
                textSize = LABEL_TEXT_SIZE
                isAntiAlias = true
            }
            val valuePaint = TextPaint().apply {
                color = Color.parseColor(VALUE_COLOR)
                textSize = LABEL_TEXT_SIZE
                isAntiAlias = true
            }

            // 식물 대표 이미지
            if (!detail.imagePath.isNullOrBlank()) {
                drawPlantImage(detail.imagePath)
                y += 16f
            }

            // 제목
            drawTextLine(detail.name, titlePaint)
            y += 24f

            // 구분선
            ensureSpace(1f)
            canvas.drawRect(MARGIN, y, MARGIN + CONTENT_WIDTH, y + 1f, dividerPaint)
            y += 1f + 14f

            // 라벨(왼쪽) + 값(오른쪽, 폭 안에서 줄바꿈) 2단 배치
            fun addRow(label: String, value: String?) {
                if (value.isNullOrBlank()) return
                ensureSpace(LABEL_TEXT_SIZE + LINE_SPACING_EXTRA)
                canvas.drawText(label, MARGIN, y + labelPaint.textSize, labelPaint)
                drawMultilineText(
                    value,
                    valuePaint,
                    x = MARGIN + LABEL_COLUMN_WIDTH,
                    width = CONTENT_WIDTH - LABEL_COLUMN_WIDTH
                )
                y += 9f
            }

            val firstDate = request.diaries.minOf { it.date }
            val lastDate = request.diaries.maxOf { it.date }
            // 시작일과 종료일이 같으면 날짜 하나만 표기
            val period = if (firstDate == lastDate) {
                firstDate.format(dateFormatter)
            } else {
                "${firstDate.format(dateFormatter)} – ${lastDate.format(dateFormatter)}"
            }
            addRow(context.getString(R.string.pdf_label_record_period), period)
            addRow(
                context.getString(R.string.pdf_label_planting_date),
                detail.plantingDate?.format(dateFormatter)
            )
            if (detail.wateringCycle > 0) {
                addRow(
                    context.getString(R.string.pdf_label_watering_cycle),
                    context.getString(R.string.pdf_watering_cycle_format, detail.wateringCycle)
                )
            }
            addRow(context.getString(R.string.pdf_label_memo), detail.memo)
            addRow(
                context.getString(R.string.pdf_label_harvest_date),
                detail.harvestDate?.format(dateFormatter)
            )
            addRow(context.getString(R.string.pdf_label_harvest_memo), detail.harvestMemo)

            // 첫 페이지 끝내고 새 페이지 시작
            finishCurrentAndStartNew()
        }

        private fun addDiaryPages(diaries: List<DiaryPdf>) {
            diaries.forEach { day ->
                val spacing = if (isPageEmpty()) 0f else DAY_SPACING
                // 하루치를 한 덩어리로 보고, 안 들어가면 날짜째 다음 페이지로 넘긴다
                val startNew = PageBreak.shouldStartNewPage(
                    blockHeight = measureDay(day),
                    headHeight = measureDayHead(day),
                    remaining = remainingHeight() - spacing,
                    pageContentHeight = PAGE_HEIGHT - MARGIN * 2,
                    isPageEmpty = isPageEmpty()
                )
                if (startNew) {
                    finishCurrentAndStartNew()
                } else {
                    y += spacing
                }
                drawDay(day)
            }
        }

        // ---- 높이 계산 (그리기와 같은 상수·레이아웃을 사용) ----

        private fun lineHeight(paint: TextPaint) = paint.textSize + LINE_SPACING_EXTRA

        private fun tasksHeight(day: DiaryPdf) =
            if (day.tasks.isEmpty()) 0f
            else day.tasks.size * lineHeight(contentPaint) + AFTER_BLOCK_GAP

        private fun imageRowHeight() = IMAGE_HEIGHT + IMAGE_GAP

        private fun imagesHeight(count: Int) = (count + 1) / 2 * imageRowHeight()

        private fun textHeight(content: String) =
            if (content.isBlank()) 0f
            else buildLayout(content, contentPaint, CONTENT_WIDTH.toInt()).lineCount *
                    lineHeight(contentPaint) + AFTER_BLOCK_GAP

        private fun entryHeight(entry: DiaryEntryPdf) =
            imagesHeight(entry.imagePaths.size) + textHeight(entry.content)

        /** 날짜 줄 + 구분선 높이 */
        private fun dateHeadHeight() = DATE_TEXT_SIZE + DIVIDER_OFFSET + 1f + DIVIDER_AFTER_GAP

        private fun measureDay(day: DiaryPdf): Float {
            var height = dateHeadHeight() + tasksHeight(day)
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) height += ENTRY_GAP
                height += entryHeight(entry)
            }
            return height
        }

        /** 긴 블록을 쪼갤 때 날짜와 함께 있어야 하는 앞부분 높이 */
        private fun measureDayHead(day: DiaryPdf): Float {
            val first = day.entries.firstOrNull()
            // 사진이 있으면 날짜 바로 아래가 사진 첫 행
            if (first != null && first.imagePaths.isNotEmpty()) {
                return dateHeadHeight() + imageRowHeight()
            }
            val height = dateHeadHeight() + tasksHeight(day)
            if (first == null || first.content.isBlank()) return height
            val lines = buildLayout(first.content, contentPaint, CONTENT_WIDTH.toInt()).lineCount
            return height + minOf(lines, HEAD_TEXT_LINES) * lineHeight(contentPaint)
        }

        // ---- 그리기 ----

        /** 날짜 → 사진 → 할일 → 내용. 할일은 하루에 한 번, 첫 일기의 사진과 내용 사이에 그린다 */
        private fun drawDay(day: DiaryPdf) {
            drawDateLine(day)
            if (day.entries.isEmpty()) drawDayTasks(day)
            day.entries.forEachIndexed { index, entry ->
                if (index > 0) y += ENTRY_GAP
                if (entry.imagePaths.isNotEmpty()) {
                    drawImageRows(entry.imagePaths)
                }
                if (index == 0) drawDayTasks(day)
                if (entry.content.isNotBlank()) {
                    drawMultilineText(entry.content, contentPaint)
                    y += AFTER_BLOCK_GAP
                }
            }
        }

        private fun drawDayTasks(day: DiaryPdf) {
            if (day.tasks.isEmpty()) return
            drawTaskLines(day.tasks)
            y += AFTER_BLOCK_GAP
        }

        /** 날짜 + 요일, 오른쪽 끝 물주기 칩, 아래 구분선 */
        private fun drawDateLine(day: DiaryPdf) {
            ensureSpace(dateHeadHeight())
            val dateText = day.date.format(dateFormatter)
            val baseline = y + datePaint.textSize
            canvas.drawText(dateText, MARGIN, baseline, datePaint)
            canvas.drawText(
                day.date.format(weekdayFormatter),
                MARGIN + datePaint.measureText("$dateText "),
                baseline,
                weekdayPaint
            )
            if (day.hasWateringRecord) drawWaterChip(y)

            val dividerY = baseline + DIVIDER_OFFSET
            canvas.drawRect(MARGIN, dividerY, MARGIN + CONTENT_WIDTH, dividerY + 1f, dividerPaint)
            y = dividerY + 1f + DIVIDER_AFTER_GAP
        }

        /** 오른쪽 정렬된 알약 모양 물주기 칩 (물방울 아이콘 + 라벨) */
        private fun drawWaterChip(top: Float) {
            val label = context.getString(R.string.pdf_watering_chip)
            val width = CHIP_PADDING * 2 + CHIP_ICON_SIZE + CHIP_ICON_GAP +
                    chipTextPaint.measureText(label)
            val right = MARGIN + CONTENT_WIDTH
            val left = right - width
            val centerY = top + CHIP_HEIGHT / 2f
            canvas.drawRoundRect(
                RectF(left, top, right, top + CHIP_HEIGHT),
                CHIP_HEIGHT / 2f,
                CHIP_HEIGHT / 2f,
                chipPaint
            )
            waterDropDrawable?.let {
                val iconLeft = (left + CHIP_PADDING).toInt()
                val iconTop = (centerY - CHIP_ICON_SIZE / 2f).toInt()
                it.setBounds(
                    iconLeft,
                    iconTop,
                    iconLeft + CHIP_ICON_SIZE.toInt(),
                    iconTop + CHIP_ICON_SIZE.toInt()
                )
                it.draw(canvas)
            }
            // 텍스트 세로 중앙 정렬
            val metrics = chipTextPaint.fontMetrics
            val baseline = centerY - (metrics.ascent + metrics.descent) / 2f
            canvas.drawText(
                label,
                left + CHIP_PADDING + CHIP_ICON_SIZE + CHIP_ICON_GAP,
                baseline,
                chipTextPaint
            )
        }

        /**
         * 할일을 갈색 원 + 한 줄 텍스트로 그린다. 제목은 진하게, 메모는 " · 메모" 회색으로 이어 붙이고
         * 전체를 폭에 맞춰 말줄임한다. 줄마다 페이지 넘김을 확인한다.
         */
        private fun drawTaskLines(tasks: List<TaskPdf>) {
            val lineHeight = lineHeight(contentPaint)
            val textLeft = MARGIN + TASK_DOT_RADIUS * 2 + TASK_DOT_GAP
            val textWidth = CONTENT_WIDTH - (textLeft - MARGIN)
            tasks.forEach { task ->
                ensureSpace(lineHeight)
                // 한 줄로 그리므로 메모의 줄바꿈은 공백으로
                val memo = task.memo?.replace(Regex("\\s+"), " ")?.trim()
                val full = if (memo.isNullOrEmpty()) task.title else "${task.title} · $memo"
                val text = TextUtils.ellipsize(
                    full, contentPaint, textWidth, TextUtils.TruncateAt.END
                ).toString()
                val baseline = y + contentPaint.textSize
                if (text.length <= task.title.length) {
                    // 제목만 보이거나 제목 자체가 잘린 경우
                    canvas.drawText(text, textLeft, baseline, contentPaint)
                } else {
                    // 제목 뒤 나머지(구분점·메모·말줄임표)는 회색으로
                    canvas.drawText(task.title, textLeft, baseline, contentPaint)
                    canvas.drawText(
                        text.substring(task.title.length),
                        textLeft + contentPaint.measureText(task.title),
                        baseline,
                        memoPaint
                    )
                }
                // 텍스트 글자 높이의 세로 중앙에 원을 맞춘다
                val centerY = y + contentPaint.textSize / 2f + 1f
                canvas.drawCircle(MARGIN + TASK_DOT_RADIUS, centerY, TASK_DOT_RADIUS, taskDotPaint)
                y += lineHeight
            }
        }

        /**
         * 단일 텍스트 라인을 그린다 (줄바꿈 없음).
         */
        private fun drawTextLine(text: String, paint: TextPaint) {
            ensureSpace(paint.textSize + LINE_SPACING_EXTRA)
            canvas.drawText(text, MARGIN, y + paint.textSize, paint)
            y += paint.textSize + LINE_SPACING_EXTRA
        }

        private fun buildLayout(text: String, paint: TextPaint, width: Int): StaticLayout =
            StaticLayout.Builder
                .obtain(text, 0, text.length, paint, width)
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(LINE_SPACING_EXTRA, 1f)
                .setIncludePad(false)
                .build()

        /**
         * 다중 라인 텍스트를 StaticLayout으로 그린다.
         * 페이지 경계를 넘으면 새 페이지에서 이어서 그린다.
         */
        private fun drawMultilineText(
            text: String,
            paint: TextPaint,
            x: Float = MARGIN,
            width: Float = CONTENT_WIDTH
        ) {
            val layout = buildLayout(text, paint, width.toInt())
            val lineHeight = lineHeight(paint)

            for (i in 0 until layout.lineCount) {
                ensureSpace(lineHeight)
                val lineStart = layout.getLineStart(i)
                val lineEnd = layout.getLineVisibleEnd(i)
                val lineText = text.substring(lineStart, lineEnd)
                canvas.drawText(lineText, x, y + paint.textSize, paint)
                y += lineHeight
            }
        }

        /**
         * 식물 대표 이미지를 전체 너비로 그린다.
         */
        private fun drawPlantImage(imagePath: String) {
            ensureSpace(PLANT_IMAGE_HEIGHT)
            // 디코딩 성패·dry-run과 무관하게 높이를 확보해 레이아웃을 일치시킨다
            val top = y
            y += PLANT_IMAGE_HEIGHT
            if (dryRun) return
            try {
                val targetWidth = (PLANT_IMAGE_WIDTH * DPI_SCALE).toInt()
                val targetHeight = (PLANT_IMAGE_HEIGHT * DPI_SCALE).toInt()

                val bitmap = decodeBitmap(imagePath, targetWidth, targetHeight) ?: return
                val cropped = cropToRatio(bitmap, 1.5f)

                @Suppress("UseKtx")
                val scaled = Bitmap.createScaledBitmap(
                    cropped,
                    targetWidth,
                    targetHeight,
                    true
                )
                val destRect = RectF(MARGIN, top, MARGIN + PLANT_IMAGE_WIDTH, top + PLANT_IMAGE_HEIGHT)
                canvas.drawBitmap(
                    scaled,
                    null,
                    destRect,
                    Paint().apply { isAntiAlias = true; isFilterBitmap = true })
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            } catch (e: Exception) {
                Timber.e(e, "식물 이미지 로드 실패: $imagePath")
            }
        }

        /**
         * 이미지를 1.5:1 비율로 crop하여 가로 2장씩 배치한다.
         */
        private fun drawImageRows(imagePaths: List<String>) {
            val rowHeight = IMAGE_HEIGHT + IMAGE_GAP

            imagePaths.chunked(2).forEach { pair ->
                ensureSpace(rowHeight)

                var x = MARGIN
                pair.forEach { path ->
                    drawImage(path, x, y)
                    x += IMAGE_WIDTH + IMAGE_GAP
                }

                y += rowHeight
            }
        }

        private fun drawImage(imagePath: String, x: Float, y: Float) {
            if (dryRun) return // 레이아웃 높이는 비트맵과 무관
            try {
                val targetWidth = (IMAGE_WIDTH * DPI_SCALE).toInt()
                val targetHeight = (IMAGE_HEIGHT * DPI_SCALE).toInt()

                val bitmap = decodeBitmap(imagePath, targetWidth, targetHeight) ?: return
                val cropped = cropToRatio(bitmap, 4f / 3f)

                @Suppress("UseKtx")
                val scaled = Bitmap.createScaledBitmap(
                    cropped,
                    targetWidth,
                    targetHeight,
                    true
                )
                val destRect = RectF(x, y, x + IMAGE_WIDTH, y + IMAGE_HEIGHT)
                canvas.drawBitmap(
                    scaled,
                    null,
                    destRect,
                    Paint().apply { isAntiAlias = true; isFilterBitmap = true })
                if (scaled !== cropped) scaled.recycle()
                if (cropped !== bitmap) cropped.recycle()
                bitmap.recycle()
            } catch (e: Exception) {
                Timber.e(e, "이미지 로드 실패: $imagePath")
            }
        }

        /**
         * 지정된 경로의 이미지를 목표 크기(reqWidth, reqHeight)에 맞춰 효율적으로 로드한다.
         * BitmapFactory.Options를 사용하여 실제 비트맵을 메모리에 올리기 전에 크기를 먼저 확인하고,
         * 적절한 inSampleSize를 계산하여 메모리 사용량을 최적화한다.
         */
        private fun decodeBitmap(path: String, reqWidth: Int, reqHeight: Int): Bitmap? {
            val options = BitmapFactory.Options().apply {
                // 실제 비트맵 데이터는 로드하지 않고 이미지의 정보(크기, 타입 등)만 읽어온다.
                inJustDecodeBounds = true
            }
            BitmapFactory.decodeFile(path, options)

            // 목표 크기에 가장 근접한(2의 거듭제곱 단위) 샘플링 비율을 계산한다.
            options.inSampleSize = calculateInSampleSize(options, reqWidth, reqHeight)
            options.inJustDecodeBounds = false
            return BitmapFactory.decodeFile(path, options)
        }

        /**
         * 원본 이미지 크기와 목표 크기를 비교하여 가장 적절한 inSampleSize 값을 계산한다.
         * inSampleSize는 2의 거듭제곱(1, 2, 4, 8...) 값을 가지며, 값이 클수록 로드되는 이미지 해상도는 낮아지고
         * 메모리는 절약된다.
         */
        private fun calculateInSampleSize(
            options: BitmapFactory.Options,
            reqWidth: Int,
            reqHeight: Int
        ): Int {
            val (height: Int, width: Int) = options.outHeight to options.outWidth
            var inSampleSize = 1

            if (height > reqHeight || width > reqWidth) {
                val halfHeight: Int = height / 2
                val halfWidth: Int = width / 2

                // 목표 크기보다 작아지기 직전까지 inSampleSize를 2배씩 키운다.
                while (halfHeight / inSampleSize >= reqHeight
                    && halfWidth / inSampleSize >= reqWidth
                ) {
                    inSampleSize *= 2
                }
            }
            return inSampleSize
        }
    }

    /**
     * 비트맵을 지정한 가로:세로 비율로 center crop한다.
     */
    private fun cropToRatio(bitmap: Bitmap, ratio: Float): Bitmap {
        val srcWidth = bitmap.width
        val srcHeight = bitmap.height
        val srcRatio = srcWidth.toFloat() / srcHeight.toFloat()

        val (cropWidth, cropHeight) = if (srcRatio > ratio) {
            // 더 넓은 경우 → 가로 크롭
            val w = (srcHeight * ratio).toInt()
            w to srcHeight
        } else {
            // 더 높은 경우 → 세로 크롭
            val h = (srcWidth / ratio).toInt()
            srcWidth to h
        }

        val x = (srcWidth - cropWidth) / 2
        val y = (srcHeight - cropHeight) / 2

        return Bitmap.createBitmap(bitmap, x, y, cropWidth, cropHeight)
    }
}
