package com.physcard.physics.di

import android.content.Context
import androidx.room.Room
import com.physcard.physics.data.db.AppDatabase
import com.physcard.physics.data.repository.ContentRepository
import com.physcard.physics.data.repository.SettingsRepository
import com.physcard.physics.data.repository.StudyRepository
import com.physcard.physics.data.seed.SeedLoader
import com.physcard.physics.domain.fsrs.FsrsSchedulingService
import com.physcard.physics.domain.level.MemoryLevelPolicy
import com.physcard.physics.domain.time.AppClock
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json

class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    val json: Json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
        prettyPrint = true
    }

    val database: AppDatabase = Room.databaseBuilder(
        appContext,
        AppDatabase::class.java,
        AppDatabase.NAME,
    )
        .addMigrations(*AppDatabase.MIGRATIONS)
        .build()

    val settingsRepository = SettingsRepository(appContext)
    val seedLoader = SeedLoader(appContext, json)
    val contentRepository = ContentRepository(database, seedLoader, json)
    val clock = AppClock()
    val fsrsScheduler = FsrsSchedulingService(clock)
    val levelPolicy = MemoryLevelPolicy()
    val studyRepository = StudyRepository(
        database = database,
        contentRepository = contentRepository,
        scheduler = fsrsScheduler,
        levelPolicy = levelPolicy,
        clock = clock,
    )

    private val initMutex = Mutex()
    @Volatile
    private var initialized = false

    suspend fun initialize() {
        if (initialized) return
        initMutex.withLock {
            if (initialized) return
            contentRepository.ensureModules()
            val settings = settingsRepository.settings.first()
            if (database.formulaDao().count() == 0 ||
                settings.seedVersion < ContentRepository.SEED_VERSION
            ) {
                contentRepository.syncBuiltInContent()
                settingsRepository.updateSeedVersion(ContentRepository.SEED_VERSION)
            }
            studyRepository.ensureReviewStates()
            fsrsScheduler.updateRetention(settings.desiredRetention)
            initialized = true
        }
    }
}
