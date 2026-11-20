package com.mskwak.file

import android.graphics.Bitmap
import java.io.File

interface FileDataSource {
    /** 실패하면 만든 파일을 지우고 예외를 던진다. */
    fun savePicture(dirPath: String, bitmap: Bitmap): File
    fun deletePicture(path: String)
    fun listPictures(dirPath: String): List<File>
}