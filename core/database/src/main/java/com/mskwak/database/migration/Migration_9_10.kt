package com.mskwak.database.migration

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

internal val MIGRATION_9_10 = object : Migration(9, 10) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE plant_task_log ADD COLUMN content TEXT")
        // 기존 기록에 현재 할일 내용을 채움. 이미 삭제된 할일의 기록은 null로 남음
        db.execSQL(
            """
            UPDATE plant_task_log
            SET content = (SELECT content FROM plant_task WHERE plant_task.id = plant_task_log.taskId)
            """.trimIndent()
        )
    }
}
