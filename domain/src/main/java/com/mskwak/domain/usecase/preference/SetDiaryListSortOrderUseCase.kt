package com.mskwak.domain.usecase.preference

import com.mskwak.domain.model.DiaryListSortOrder
import com.mskwak.domain.repository.UserPreferenceRepository

class SetDiaryListSortOrderUseCase(
    private val userPreferenceRepository: UserPreferenceRepository
) {
    suspend operator fun invoke(order: DiaryListSortOrder) {
        userPreferenceRepository.setDiaryListSortOrder(order)
    }
}
