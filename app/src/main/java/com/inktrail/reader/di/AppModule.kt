package com.inktrail.reader.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.inktrail.reader.data.local.db.AnnotationDao
import com.inktrail.reader.data.local.db.BookDao
import com.inktrail.reader.data.local.db.ChapterDao
import com.inktrail.reader.data.local.db.InkTrailDatabase
import com.inktrail.reader.data.local.prefs.ReaderPreferencesDataStore
import com.inktrail.reader.data.local.storage.AndroidBookFileStorage
import com.inktrail.reader.data.local.storage.BookFileStorage
import com.inktrail.reader.data.local.storage.ChapterByteReader
import com.inktrail.reader.data.local.storage.FileChannelChapterReader
import com.inktrail.reader.data.repository.AnnotationRepositoryImpl
import com.inktrail.reader.data.repository.BookRepositoryImpl
import com.inktrail.reader.domain.repository.AnnotationRepository
import com.inktrail.reader.domain.repository.BookRepository
import com.inktrail.reader.domain.repository.ReaderPreferencesRepository
import com.inktrail.reader.engine.annotation.HighlightIntervalManager
import com.inktrail.reader.engine.cleaner.TextCleaner
import com.inktrail.reader.engine.encoding.CharsetDetector
import com.inktrail.reader.engine.paginator.Paginator
import com.inktrail.reader.engine.search.BookSearchEngine
import com.inktrail.reader.engine.splitter.ChapterSplitter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import javax.inject.Qualifier
import javax.inject.Singleton

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class IoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DefaultDispatcher

private val Context.readerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "reader_preferences"
)

@Module
@InstallIn(SingletonComponent::class)
object EngineAndStorageModule {

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    @Provides
    @Singleton
    fun provideCharsetDetector(): CharsetDetector = CharsetDetector()

    @Provides
    @Singleton
    fun provideChapterSplitter(): ChapterSplitter = ChapterSplitter()

    @Provides
    @Singleton
    fun provideTextCleaner(): TextCleaner = TextCleaner()

    @Provides
    @Singleton
    fun providePaginator(): Paginator = Paginator()

    @Provides
    @Singleton
    fun provideHighlightIntervalManager(): HighlightIntervalManager = HighlightIntervalManager()

    @Provides
    @Singleton
    fun provideBookSearchEngine(): BookSearchEngine = BookSearchEngine()

    @Provides
    @Singleton
    fun provideInkTrailDatabase(
        @ApplicationContext context: Context
    ): InkTrailDatabase = Room.databaseBuilder(
        context,
        InkTrailDatabase::class.java,
        InkTrailDatabase.DATABASE_NAME
    ).build()

    @Provides
    fun provideBookDao(db: InkTrailDatabase): BookDao = db.bookDao()

    @Provides
    fun provideChapterDao(db: InkTrailDatabase): ChapterDao = db.chapterDao()

    @Provides
    fun provideAnnotationDao(db: InkTrailDatabase): AnnotationDao = db.annotationDao()

    @Provides
    @Singleton
    fun providePreferencesDataStore(
        @ApplicationContext context: Context
    ): DataStore<Preferences> = context.readerPreferencesDataStore
}

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryBindingsModule {

    @Binds
    @Singleton
    abstract fun bindBookFileStorage(impl: AndroidBookFileStorage): BookFileStorage

    @Binds
    @Singleton
    abstract fun bindChapterByteReader(impl: FileChannelChapterReader): ChapterByteReader

    @Binds
    @Singleton
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    @Singleton
    abstract fun bindReaderPreferencesRepository(
        impl: ReaderPreferencesDataStore
    ): ReaderPreferencesRepository

    @Binds
    @Singleton
    abstract fun bindAnnotationRepository(
        impl: AnnotationRepositoryImpl
    ): AnnotationRepository
}
