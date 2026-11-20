package com.mskwak.data.repository

import android.graphics.BitmapFactory
import com.mskwak.database.dao.PictureDao
import com.mskwak.domain.model.Picture
import com.mskwak.domain.repository.PictureRepository
import com.mskwak.file.FileDataSource
import java.io.File
import javax.inject.Inject
import timber.log.Timber

internal class PictureRepositoryImpl @Inject constructor(
    private val fileDataSource: FileDataSource,
    private val pictureDao: PictureDao
) : PictureRepository {
    override suspend fun savePicture(bitmap: ByteArray): Picture {
        val decodedBitmap = BitmapFactory.decodeByteArray(bitmap, 0, bitmap.size)
            ?: throw IllegalArgumentException("cannot decode picture")
        val file = fileDataSource.savePicture(PLANT_PICTURE_DIR, decodedBitmap)

        return Picture(
            path = file.path,
            fileName = file.name,
            createdAt = System.currentTimeMillis()
        )
    }

    override suspend fun deletePicture(picture: Picture) {
        fileDataSource.deletePicture(picture.path)
    }

    override suspend fun deleteUnusedPictures(createdBefore: Long): Int {
        // 경로 형식이 버전마다 달라(절대 경로, file:// URI) 파일명으로 비교한다.
        val pictures = pictureDao.getAllPictures()
        val usedNames = pictures.flatMapTo(HashSet()) { listOf(File(it.path).name, it.fileName) }
        val files = fileDataSource.listPictures(PLANT_PICTURE_DIR)

        // 편집 중 아직 저장하지 않은 사진은 DB에 없으므로 오래된 파일만 지운다.
        val unused = files.filter { it.name !in usedNames && it.lastModified() < createdBefore }
        unused.forEach { fileDataSource.deletePicture(it.path) }

        // DB에는 있는데 파일이 없는 경우는 복구할 방법이 없어 기록만 남긴다.
        val fileNames = files.mapTo(HashSet()) { it.name }
        val missing = pictures.count { File(it.path).name !in fileNames && it.fileName !in fileNames }
        if (missing > 0) Timber.w("picture files missing: $missing")
        Timber.d("deleted unused pictures: ${unused.size}")
        return unused.size
    }

    companion object {
        private const val PLANT_PICTURE_DIR = "plantPicture"
    }
}
