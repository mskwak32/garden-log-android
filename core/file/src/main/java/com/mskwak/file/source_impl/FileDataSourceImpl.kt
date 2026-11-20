package com.mskwak.file.source_impl

import android.app.Application
import android.graphics.Bitmap
import com.mskwak.file.Constants
import com.mskwak.file.FileDataSource
import timber.log.Timber
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.UUID
import javax.inject.Inject

internal class FileDataSourceImpl @Inject constructor(
    application: Application
) : FileDataSource {
    private val baseDir = application.filesDir

    override fun savePicture(
        dirPath: String,
        bitmap: Bitmap
    ): File {
        val dir = File(baseDir, dirPath)

        if (!dir.exists()) {
            dir.mkdirs()
        }

        val file = File(dir, "${UUID.randomUUID()}.jpg")

        try {
            val compressed = FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, Constants.FILE_COMPRESS_QUALITY, out)
            }
            if (!compressed) throw IOException("bitmap compress failed")
        } catch (e: Exception) {
            // 깨진 파일이 DB에 기록되지 않도록 지우고 호출부에 실패를 알린다.
            file.delete()
            Timber.e(e, "save picture error")
            throw e
        }

        Timber.d("save picture: ${file.path}")
        return file
    }

    override fun deletePicture(path: String) {
        File(path).run {
            if (exists()) {
                delete()
                Timber.d("delete picture: $path")
            }
        }
    }

    override fun listPictures(dirPath: String): List<File> =
        File(baseDir, dirPath).listFiles()?.filter { it.isFile } ?: emptyList()
}