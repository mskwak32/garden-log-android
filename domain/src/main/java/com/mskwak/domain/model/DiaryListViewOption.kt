package com.mskwak.domain.model

enum class DiaryListPeriodUnit {
    MONTH,
    YEAR
}

/**
 * 일기 탭 보기 설정. 단위와 정렬은 항상 함께 쓰이므로 하나로 묶는다.
 */
data class DiaryListViewOption(
    val periodUnit: DiaryListPeriodUnit,
    val sortOrder: DiaryListSortOrder
)
