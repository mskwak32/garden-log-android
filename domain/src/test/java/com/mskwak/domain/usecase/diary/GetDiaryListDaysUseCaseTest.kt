package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.Diary
import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.model.PlantTaskLog
import com.mskwak.domain.model.WateringLog
import com.mskwak.domain.repository.DiaryRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.repository.WateringLogRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.YearMonth

class GetDiaryListDaysUseCaseTest {
    private val day1 = LocalDate.of(2025, 3, 1)
    private val day2 = LocalDate.of(2025, 3, 5)

    private val diary = Diary(1, 1, "메모", null, day1)
    private val taskLog = PlantTaskLog(plantId = 1, taskId = 1, title = "분갈이", date = day1, id = 1)
    private val wateringLog = WateringLog(plantId = 1, date = day1)
    private val laterWatering = WateringLog(plantId = 1, date = day2)

    @Test
    fun 같은_날짜의_일기_할일_물주기가_하루치로_묶인다() = runBlocking {
        val result = useCase().invoke(
            DiaryListPeriodUnit.MONTH, YearMonth.of(2025, 3), DiaryListSortOrder.CREATED_LATEST
        ).first()

        val day = result.first { it.date == day1 }
        assertEquals(listOf(diary), day.diaries)
        assertEquals(listOf(taskLog), day.taskLogs)
        assertEquals(listOf(wateringLog), day.wateringLogs)
    }

    @Test
    fun 정렬_설정에_따라_날짜_순서가_바뀐다() = runBlocking {
        fun dates(order: DiaryListSortOrder) = runBlocking {
            useCase().invoke(DiaryListPeriodUnit.MONTH, YearMonth.of(2025, 3), order)
                .first().map { it.date }
        }

        assertEquals(listOf(day2, day1), dates(DiaryListSortOrder.CREATED_LATEST))
        assertEquals(listOf(day1, day2), dates(DiaryListSortOrder.CREATED_EARLIEST))
    }

    // 사용하는 조회 메서드만 고정 값을 돌려주고 나머지는 호출 시 실패
    private fun useCase() = GetDiaryListDaysUseCase(
        stub<DiaryRepository>("getDiaries", flowOf(listOf(diary))),
        stub<PlantTaskRepository>("observeTaskLogsByRange", flowOf(listOf(taskLog))),
        stub<WateringLogRepository>(
            "observeWateringLogsByRange", flowOf(listOf(wateringLog, laterWatering))
        )
    )

    private inline fun <reified T> stub(methodName: String, value: Any): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, method, _ ->
            if (method.name == methodName) value else error("unused: ${method.name}")
        } as T
    }
}
