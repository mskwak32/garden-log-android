package com.mskwak.domain.usecase.preference

import com.mskwak.domain.model.PlantListSortOrder
import com.mskwak.domain.repository.UserPreferenceRepository
import kotlinx.coroutines.flow.Flow

class GetPlantListSortOrderUseCase(
    private val userPreferenceRepository: UserPreferenceRepository
) {
    operator fun invoke(): Flow<PlantListSortOrder> = userPreferenceRepository.plantListSortOrder
}
