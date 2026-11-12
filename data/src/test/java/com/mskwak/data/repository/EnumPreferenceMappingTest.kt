package com.mskwak.data.repository

import com.mskwak.domain.model.DiaryListPeriodUnit
import org.junit.Assert.assertEquals
import org.junit.Test

class EnumPreferenceMappingTest {
    @Test
    fun 저장된_이름이_있으면_해당_enum으로_변환한다() {
        assertEquals(DiaryListPeriodUnit.YEAR, "YEAR".toEnumOrDefault(DiaryListPeriodUnit.MONTH))
    }

    @Test
    fun 저장값이_없거나_알_수_없는_이름이면_기본값을_쓴다() {
        assertEquals(DiaryListPeriodUnit.MONTH, null.toEnumOrDefault(DiaryListPeriodUnit.MONTH))
        assertEquals(DiaryListPeriodUnit.MONTH, "WEEK".toEnumOrDefault(DiaryListPeriodUnit.MONTH))
    }
}
