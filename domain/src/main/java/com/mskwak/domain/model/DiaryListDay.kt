package com.mskwak.domain.model

import java.time.LocalDate

/**
 * 일기 목록의 하루치 기록 묶음. 세 목록 중 최소 하나는 비어 있지 않다.
 */
data class DiaryListDay(
    val date: LocalDate,
    val diaries: List<Diary>,
    val taskLogs: List<PlantTaskLog>,
    val wateringLogs: List<WateringLog>
)
