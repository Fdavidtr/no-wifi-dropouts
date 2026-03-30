package io.multinet.mobility.di

import android.content.Context
import android.net.ConnectivityDiagnosticsManager
import android.net.ConnectivityManager
import androidx.datastore.core.DataStore
import androidx.datastore.core.DataStoreFactory
import androidx.datastore.dataStoreFile
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.multinet.mobility.data.db.AppDatabase
import io.multinet.mobility.data.db.EventLogDao
import io.multinet.mobility.data.db.SignalSampleDao
import io.multinet.mobility.data.preferences.UserPreferencesMigration
import io.multinet.mobility.data.preferences.UserPreferencesSerializer
import io.multinet.mobility.datastore.MobilitySettings
import java.time.Clock
import java.util.concurrent.Executor
import java.util.concurrent.Executors
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

@Qualifier
annotation class ApplicationScope

@Module
@InstallIn(SingletonComponent::class)
object AppModules {
    @Provides
    @Singleton
    fun provideDataStore(
        @ApplicationContext context: Context,
    ): DataStore<MobilitySettings> = DataStoreFactory.create(
        migrations = listOf(UserPreferencesMigration),
        serializer = UserPreferencesSerializer,
        produceFile = { context.dataStoreFile("mobility_settings.pb") },
    )

    @Provides
    @Singleton
    fun provideConnectivityManager(
        @ApplicationContext context: Context,
    ): ConnectivityManager = context.getSystemService(ConnectivityManager::class.java)

    @Provides
    @Singleton
    fun provideConnectivityDiagnosticsManager(
        @ApplicationContext context: Context,
    ): ConnectivityDiagnosticsManager? = context.getSystemService(ConnectivityDiagnosticsManager::class.java)

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
    ): AppDatabase = Room.databaseBuilder(
        context,
        AppDatabase::class.java,
        "multi_net.db",
    ).fallbackToDestructiveMigration(dropAllTables = true).build()

    @Provides
    fun provideEventLogDao(database: AppDatabase): EventLogDao = database.eventLogDao()

    @Provides
    fun provideSignalSampleDao(database: AppDatabase): SignalSampleDao = database.signalSampleDao()

    @Provides
    @Singleton
    fun provideClock(): Clock = Clock.systemUTC()

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides
    @Singleton
    fun provideWifiCallbackExecutor(): Executor = Executors.newSingleThreadExecutor()
}
