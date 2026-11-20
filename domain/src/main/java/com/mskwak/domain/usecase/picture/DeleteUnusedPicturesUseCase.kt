package com.mskwak.domain.usecase.picture

import com.mskwak.domain.repository.PictureRepository

/** 식물·일기에 연결되지 않은 사진 파일을 정리한다. */
class DeleteUnusedPicturesUseCase(
    private val pictureRepository: PictureRepository
) {
    suspend operator fun invoke(now: Long = System.currentTimeMillis()): Int =
        pictureRepository.deleteUnusedPictures(createdBefore = now - MIN_AGE_MILLIS)

    companion object {
        // 편집 화면이 열려 있는 동안 고른 사진을 지우지 않도록 이틀 여유를 둔다.
        const val MIN_AGE_MILLIS = 2 * 24 * 60 * 60 * 1000L
    }
}
