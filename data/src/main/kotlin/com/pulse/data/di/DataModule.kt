package com.pulse.data.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.room.Room
import androidx.work.WorkManager
import com.pulse.data.BuildConfig
import com.pulse.data.cloud.GoogleHealthAuthManager
import com.pulse.data.cloud.GoogleHealthRemoteDataSource
import com.pulse.data.cloud.GoogleHealthRestClient
import com.pulse.data.cloud.NoopGoogleHealthRemoteDataSource
import com.pulse.data.datastore.FeatureFlagsSerializer
import com.pulse.data.datastore.PreferencesSerializer
import com.pulse.data.local.PulseDatabase
import com.pulse.data.local.dao.DailyAggregateDao
import com.pulse.data.local.dao.ExerciseSessionDao
import com.pulse.data.local.dao.GoalDao
import com.pulse.data.local.dao.HealthSampleDao
import com.pulse.data.local.dao.SleepSessionDao
import com.pulse.data.local.dao.SyncStateDao
import com.pulse.data.proto.FeatureFlags
import com.pulse.data.proto.Preferences
import com.pulse.data.repository.DebugRepositoryImpl
import com.pulse.data.repository.DeviceStatusRepositoryImpl
import com.pulse.data.repository.GoalsRepositoryImpl
import com.pulse.data.repository.HealthRepositoryImpl
import com.pulse.data.repository.SyncRepositoryImpl
import com.pulse.domain.repository.DebugRepository
import com.pulse.domain.repository.DeviceStatusRepository
import com.pulse.domain.repository.GoalsRepository
import com.pulse.domain.repository.HealthRepository
import com.pulse.domain.repository.SyncRepository
import com.pulse.domain.util.Clock
import com.pulse.domain.util.SystemClock
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DataProvidersModule {

    @Provides
    @Singleton
    fun database(@ApplicationContext ctx: Context): PulseDatabase =
        Room.databaseBuilder(ctx, PulseDatabase::class.java, PulseDatabase.NAME)
            .addMigrations(
                PulseDatabase.MIGRATION_2_3,
                PulseDatabase.MIGRATION_3_4,
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun dailyAggregateDao(db: PulseDatabase): DailyAggregateDao = db.dailyAggregateDao()
    @Provides fun exerciseDao(db: PulseDatabase): ExerciseSessionDao = db.exerciseSessionDao()
    @Provides fun exerciseHrSampleDao(db: PulseDatabase): com.pulse.data.local.dao.ExerciseHrSampleDao = db.exerciseHrSampleDao()
    @Provides fun exerciseLapDao(db: PulseDatabase): com.pulse.data.local.dao.ExerciseLapDao = db.exerciseLapDao()
    @Provides fun exerciseRoutePointDao(db: PulseDatabase): com.pulse.data.local.dao.ExerciseRoutePointDao = db.exerciseRoutePointDao()
    @Provides fun sampleDao(db: PulseDatabase): HealthSampleDao = db.healthSampleDao()
    @Provides fun syncStateDao(db: PulseDatabase): SyncStateDao = db.syncStateDao()
    @Provides fun goalDao(db: PulseDatabase): GoalDao = db.goalDao()
    @Provides fun sleepDao(db: PulseDatabase): SleepSessionDao = db.sleepSessionDao()

    @Provides
    @Singleton
    fun featureFlagsStore(@ApplicationContext ctx: Context): DataStore<FeatureFlags> =
        DataStoreFactory.create(
            serializer = FeatureFlagsSerializer,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { ctx.dataStoreFile("feature_flags.pb") },
        )

    @Provides
    @Singleton
    fun preferencesStore(@ApplicationContext ctx: Context): DataStore<Preferences> =
        DataStoreFactory.create(
            serializer = PreferencesSerializer,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            produceFile = { ctx.dataStoreFile("preferences.pb") },
        )

    @Provides
    @Singleton
    fun workManager(@ApplicationContext ctx: Context): WorkManager = WorkManager.getInstance(ctx)

    @Provides
    @Singleton
    fun httpClient(): HttpClient = HttpClient(OkHttp) {
        install(ContentNegotiation) {
            json(kotlinx.serialization.json.Json {
                ignoreUnknownKeys = true
                isLenient = true
            })
        }
        install(Logging) { level = LogLevel.HEADERS }
    }

    @Provides
    @Singleton
    fun googleHealth(
        authManager: GoogleHealthAuthManager,
        restClient: GoogleHealthRestClient,
    ): GoogleHealthRemoteDataSource =
        if (BuildConfig.GOOGLE_HEALTH_WEB_CLIENT_ID.isNotBlank()) restClient
        else NoopGoogleHealthRemoteDataSource()

    @Provides
    @Singleton
    fun clock(): Clock = SystemClock

    @Provides
    @Singleton
    fun deviceId(): String = "primary-device"

    private fun Context.dataStoreFile(name: String) =
        java.io.File(this.filesDir, "datastore/$name")
}

@Module
@InstallIn(SingletonComponent::class)
abstract class DataBindsModule {
    @Binds @Singleton abstract fun healthRepository(impl: HealthRepositoryImpl): HealthRepository
    @Binds @Singleton abstract fun syncRepository(impl: SyncRepositoryImpl): SyncRepository
    @Binds @Singleton abstract fun goalsRepository(impl: GoalsRepositoryImpl): GoalsRepository
    @Binds @Singleton abstract fun deviceStatusRepository(impl: DeviceStatusRepositoryImpl): DeviceStatusRepository
    @Binds @Singleton abstract fun debugRepository(impl: DebugRepositoryImpl): DebugRepository
}
