package com.mskwak.data.repository

import android.graphics.Bitmap
import androidx.room.Room
import com.mskwak.database.GardenDatabase
import com.mskwak.database.entity.PictureEntity
import com.mskwak.file.FileDataSource
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class UnusedPictureCleanupTest {
    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var database: GardenDatabase
    private lateinit var repository: PictureRepositoryImpl
    private lateinit var pictureDir: File

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            RuntimeEnvironment.getApplication(), GardenDatabase::class.java
        ).allowMainThreadQueries().build()
        pictureDir = tempFolder.newFolder("plantPicture")
        repository = PictureRepositoryImpl(DirFileDataSource(pictureDir), database.pictureDao())
    }

    @After
    fun tearDown() = database.close()

    @Test
    fun deletesOnlyOldFilesNotInDatabase() = runBlocking {
        val now = 10 * DAY
        val usedByPath = picture("used.jpg", now - 5 * DAY)
        val usedByUri = picture("legacy.jpg", now - 5 * DAY)
        picture("orphan_old.jpg", now - 2 * DAY)
        picture("orphan_recent.jpg", now - DAY / 2)
        database.pictureDao().insertPicture(entity(usedByPath.path, usedByPath.name))
        // 3→4 마이그레이션 데이터는 경로가 file:// URI로 남아 있다.
        database.pictureDao().insertPicture(entity("file://${usedByUri.path}", "unknown"))

        val deleted = repository.deleteUnusedPictures(createdBefore = now - DAY)

        assertEquals(1, deleted)
        assertEquals(
            setOf("used.jpg", "legacy.jpg", "orphan_recent.jpg"),
            pictureDir.list()!!.toSet()
        )
    }

    private fun picture(name: String, lastModified: Long): File =
        File(pictureDir, name).apply {
            writeText(name)
            setLastModified(lastModified)
        }

    private fun entity(path: String, fileName: String) =
        PictureEntity(path = path, fileName = fileName, createdAt = 0)

    private class DirFileDataSource(private val dir: File) : FileDataSource {
        override fun savePicture(dirPath: String, bitmap: Bitmap): File = error("not used")
        override fun deletePicture(path: String) {
            File(path).delete()
        }
        override fun listPictures(dirPath: String): List<File> = dir.listFiles()!!.toList()
    }

    private companion object {
        const val DAY = 24 * 60 * 60 * 1000L
    }
}
