package com.mskwak.domain.usecase.preference

import com.mskwak.domain.model.PlantListSortOrder
import com.mskwak.domain.repository.UserPreferenceRepository

class SetPlantListSortOrderUseCase(
    private val userPreferenceRepository: UserPreferenceRepository
) {
    suspend operator fun invoke(order: PlantListSortOrder) {
        userPreferenceRepository.setPlantListSortOrder(order)
    }
}
