package com.inktrail.reader.data.local.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        BookEntity::class,
        ChapterEntity::class,
        AnnotationEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class InkTrailDatabase : RoomDatabase() {
    abstract fun bookDao(): BookDao
    abstract fun chapterDao(): ChapterDao
    abstract fun annotationDao(): AnnotationDao

    companion object {
        const val DATABASE_NAME = "inktrail.db"
    }
}
