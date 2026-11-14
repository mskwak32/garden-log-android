package com.mskwak.database

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.mskwak.database.migration.MIGRATION_8_9
import com.mskwak.database.migration.MIGRATION_9_10
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [29])
class MigrationTest {
    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        GardenDatabase::class.java,
        emptyList(),
        AbsolutePathOpenHelperFactory()
    )

    /**
     * SupportSQLiteDriver는 DB 이름을 '/' 기준으로만 잘라 비교해서 Windows 경로('\')에서는 항상 실패한다.
     * 이름 대신 Room이 요청하는 절대 경로를 그대로 노출해 OS와 관계없이 일치시키기 위한 우회.
     */
    private class AbsolutePathOpenHelperFactory(
        private val delegate: SupportSQLiteOpenHelper.Factory = FrameworkSQLiteOpenHelperFactory()
    ) : SupportSQLiteOpenHelper.Factory {
        override fun create(configuration: SupportSQLiteOpenHelper.Configuration): SupportSQLiteOpenHelper {
            val openHelper = delegate.create(configuration)
            return object : SupportSQLiteOpenHelper by openHelper {
                override val databaseName: String?
                    get() = configuration.name?.let { configuration.context.getDatabasePath(it).absolutePath }
            }
        }
    }

    @Test
    fun migrate8To9CreatesTaskTablesMatchingSchema() {
        helper.createDatabase("migration-test", 8).close()
        helper.runMigrationsAndValidate("migration-test", 9, true, MIGRATION_8_9).close()
    }

    @Test
    fun migrate9To10BackfillsTaskLogContent() {
        helper.createDatabase("migration-test", 9).apply {
            execSQL(
                "INSERT INTO plant_task (id, plantId, title, content, type, date, createdAt, alarm_time, alarm_isActive) " +
                    "VALUES (1, 1, '물주기', '메모', 'ONE_TIME', '2026-01-01', '2026-01-01T00:00', '09:00', 0)"
            )
            execSQL("INSERT INTO plant_task_log (plantId, taskId, title, date) VALUES (1, 1, '물주기', '2026-01-01')")
            // 이미 삭제된 할일의 기록
            execSQL("INSERT INTO plant_task_log (plantId, taskId, title, date) VALUES (1, 99, '삭제됨', '2026-01-01')")
            close()
        }

        val db = helper.runMigrationsAndValidate("migration-test", 10, true, MIGRATION_9_10)
        db.query("SELECT taskId, content FROM plant_task_log ORDER BY taskId").use { cursor ->
            cursor.moveToNext()
            assertEquals("메모", cursor.getString(1))
            cursor.moveToNext()
            assertNull(cursor.getString(1))
        }
        db.close()
    }
}
