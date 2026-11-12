package com.mskwak.domain.usecase.preference

import com.mskwak.domain.model.DiaryListPeriodUnit
import com.mskwak.domain.repository.UserPreferenceRepository

class SetDiaryListPeriodUnitUseCase(
    private val userPreferenceRepository: UserPreferenceRepository
) {
    suspend operator fun invoke(unit: DiaryListPeriodUnit) {
        userPreferenceRepository.setDiaryListPeriodUnit(unit)
    }
}
