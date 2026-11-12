package com.mskwak.domain.usecase.preference

import com.mskwak.domain.model.DiaryListViewOption
import com.mskwak.domain.repository.UserPreferenceRepository
import kotlinx.coroutines.flow.Flow

class GetDiaryListViewOptionUseCase(
    private val userPreferenceRepository: UserPreferenceRepository
) {
    operator fun invoke(): Flow<DiaryListViewOption> = userPreferenceRepository.diaryListViewOption
}
