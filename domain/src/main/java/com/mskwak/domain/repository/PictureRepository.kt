package com.mskwak.domain.repository

import com.mskwak.domain.model.Picture

interface PictureRepository {
    /** 디코딩·파일 저장에 실패하면 예외를 던진다. */
    suspend fun savePicture(bitmap: ByteArray): Picture
    suspend fun deletePicture(picture: Picture)

    /** DB에 없는 사진 파일 중 [createdBefore] 이전에 만든 것을 지우고 지운 개수를 반환한다. */
    suspend fun deleteUnusedPictures(createdBefore: Long): Int
}