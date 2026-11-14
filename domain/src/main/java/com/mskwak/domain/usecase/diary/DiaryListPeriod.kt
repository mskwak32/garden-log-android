package com.mskwak.domain.usecase.diary

import com.mskwak.domain.model.DiaryListPeriodUnit
import java.time.LocalDate
import java.time.YearMonth

internal fun diaryListPeriodRange(
    periodUnit: DiaryListPeriodUnit,
    yearMonth: YearMonth
): Pair<LocalDate, LocalDate> {
    return when (periodUnit) {
        DiaryListPeriodUnit.MONTH -> yearMonth.atDay(1) to yearMonth.atEndOfMonth()
        DiaryListPeriodUnit.YEAR -> {
            LocalDate.of(yearMonth.year, 1, 1) to LocalDate.of(yearMonth.year, 12, 31)
        }
    }
}
