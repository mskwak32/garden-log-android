package com.mskwak.domain.usecase.export

import com.mskwak.domain.model.Diary
import com.mskwak.domain.model.PlantTaskLog
import com.mskwak.domain.repository.DiaryRepository
import com.mskwak.domain.repository.PlantTaskRepository
import com.mskwak.domain.repository.WateringLogRepository
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate

class GetDiaryExportDaysUseCaseTest {
    private val start = LocalDate.of(2026, 4, 1)
    private val end = LocalDate.of(2026, 10, 31)
    private val diaryDay = LocalDate.of(2026, 4, 12)
    private val wateringOnlyDay = LocalDate.of(2026, 5, 3)
    private val taskOnlyDay = LocalDate.of(2026, 4, 1)

    @Test
    fun 일기가_없어도_물주기나_할일만_있는_날이_날짜순으로_포함된다() = runBlocking {
        val result = useCase(
            diaries = listOf(Diary(1, 1, "싹이 올라옴", null, diaryDay)),
            wateringDates = listOf(wateringOnlyDay),
            taskLogs = listOf(taskLog(taskOnlyDay, "웃거름 주기"))
        ).invoke(1, start, end)

        assertEquals(listOf(taskOnlyDay, diaryDay, wateringOnlyDay), result.map { it.date })
        val wateringOnly = result.first { it.date == wateringOnlyDay }
        assertTrue(wateringOnly.entries.isEmpty())
        assertTrue(wateringOnly.hasWateringRecord)
        assertEquals(listOf("웃거름 주기"), result.first { it.date == taskOnlyDay }.tasks.map { it.title })
    }

    @Test
    fun 같은_날의_일기_여러개와_물주기_할일이_하루치로_묶인다() = runBlocking {
        val result = useCase(
            diaries = listOf(
                Diary(1, 1, "아침", null, diaryDay),
                Diary(2, 1, "저녁", null, diaryDay)
            ),
            wateringDates = listOf(diaryDay),
            taskLogs = listOf(taskLog(diaryDay, "지지대 세우기", memo = "두 개"))
        ).invoke(1, start, end)

        val day = result.single()
        assertEquals(listOf("아침", "저녁"), day.entries.map { it.content })
        assertTrue(day.hasWateringRecord)
        assertEquals("두 개", day.tasks.single().memo)
    }

    @Test
    fun 세_기록이_모두_없으면_빈_목록() = runBlocking {
        val result = useCase(emptyList(), emptyList(), emptyList()).invoke(1, start, end)

        assertTrue(result.isEmpty())
    }

    private fun taskLog(date: LocalDate, title: String, memo: String? = null) =
        PlantTaskLog(plantId = 1, taskId = 1, title = title, date = date, content = memo)

    private fun useCase(
        diaries: List<Diary>,
        wateringDates: List<LocalDate>,
        taskLogs: List<PlantTaskLog>
    ) = GetDiaryExportDaysUseCase(
        stub<DiaryRepository>("getDiariesByPlantIdAndDateRange", diaries),
        stub<WateringLogRepository>("getWateringDatesByRange", wateringDates),
        stub<PlantTaskRepository>("getPlantTaskLogsByRange", taskLogs)
    )

    // 사용하는 조회 메서드만 고정 값을 돌려주고 나머지는 호출 시 실패
    private inline fun <reified T> stub(methodName: String, value: Any): T {
        return Proxy.newProxyInstance(
            T::class.java.classLoader,
            arrayOf(T::class.java)
        ) { _, method, _ ->
            if (method.name == methodName) value else error("unused: ${method.name}")
        } as T
    }
}
