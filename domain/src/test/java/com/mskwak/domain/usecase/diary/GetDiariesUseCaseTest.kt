package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.repository.DiaryRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import java.lang.reflect.Proxy
import java.time.LocalDate
import java.time.YearMonth

class GetDiariesUseCaseTest {
    @Test
    fun 월별은_해당_월_1일부터_말일까지_조회한다() = runBlocking {
        val (repo, range) = recordingRepository()

        GetDiariesUseCase(repo)(
            periodUnit = DiaryListPeriodUnit.MONTH,
            yearMonth = YearMonth.of(2024, 2),
            sortOrder = DiaryListSortOrder.CREATED_LATEST
        ).first()

        assertEquals(LocalDate.of(2024, 2, 1) to LocalDate.of(2024, 2, 29), range())
    }

    @Test
    fun 연도별은_기준_월과_상관없이_1월1일부터_12월31일까지_조회한다() = runBlocking {
        val (repo, range) = recordingRepository()

        GetDiariesUseCase(repo)(
            periodUnit = DiaryListPeriodUnit.YEAR,
            yearMonth = YearMonth.of(2025, 7),
            sortOrder = DiaryListSortOrder.CREATED_LATEST
        ).first()

        assertEquals(LocalDate.of(2025, 1, 1) to LocalDate.of(2025, 12, 31), range())
    }

    // getDiaries에 전달된 기간을 기록한다. 나머지 메서드는 사용하지 않음
    private fun recordingRepository(): Pair<DiaryRepository, () -> Pair<LocalDate, LocalDate>?> {
        var range: Pair<LocalDate, LocalDate>? = null
        val repo = Proxy.newProxyInstance(
            DiaryRepository::class.java.classLoader,
            arrayOf(DiaryRepository::class.java)
        ) { _, method, args ->
            if (method.name == "getDiaries") {
                range = args[0] as LocalDate to args[1] as LocalDate
                flowOf(emptyList<Any>())
            } else {
                error("unused: ${method.name}")
            }
        } as DiaryRepository
        return repo to { range }
    }
}
