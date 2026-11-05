package no.scamguardian.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import no.scamguardian.app.ble.BleBoardProvisioner
import no.scamguardian.app.ble.FakeBoardProvisioner
import no.scamguardian.app.board.MockBoardClient
import no.scamguardian.app.data.BoardStore
import no.scamguardian.app.data.EventSyncer
import no.scamguardian.app.data.RoomEventStore
import no.scamguardian.app.data.TokenCipher
import no.scamguardian.app.data.db.EventDao
import no.scamguardian.app.data.db.EventDatabase
import no.scamguardian.app.discovery.BoardDiscovery
import no.scamguardian.app.discovery.NsdBoardDiscovery
import no.scamguardian.app.monitor.BoardMonitor
import no.scamguardian.app.monitor.BoardSession
import no.scamguardian.app.monitor.Notifier
import okhttp3.OkHttpClient

@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class AppScope

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "guardian")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton @AppScope
    fun appScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Provides @Singleton
    fun okHttp(): OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(5, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .writeTimeout(10, TimeUnit.SECONDS)
        // The board pings every 15 s; our own pings find a board that vanished without closing.
        .pingInterval(20, TimeUnit.SECONDS)
        .build()

    @Provides @Singleton
    fun dataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.dataStore

    @Provides @Singleton
    fun boardStore(ds: DataStore<Preferences>): BoardStore = BoardStore(ds, TokenCipher.keystore())

    @Provides @Singleton
    fun database(@ApplicationContext context: Context): EventDatabase =
        Room.databaseBuilder(context, EventDatabase::class.java, "events.db").build()

    @Provides
    fun eventDao(db: EventDatabase): EventDao = db.events()

    @Provides @Singleton
    fun eventStore(dao: EventDao): RoomEventStore = RoomEventStore(dao)

    @Provides @Singleton
    fun syncer(store: RoomEventStore): EventSyncer = EventSyncer(store)

    @Provides @Singleton
    fun simulatedBoard(@AppScope scope: CoroutineScope): MockBoardClient = MockBoardClient(scope)

    @Provides @Singleton
    fun session(
        store: BoardStore,
        okHttp: OkHttpClient,
        simulated: dagger.Lazy<MockBoardClient>,
        @AppScope scope: CoroutineScope,
    ): BoardSession = BoardSession(store, okHttp, { simulated.get() }, scope)

    @Provides @Singleton
    fun notifier(@ApplicationContext context: Context): Notifier = Notifier(context)

    @Provides @Singleton
    fun discovery(@ApplicationContext context: Context): BoardDiscovery = NsdBoardDiscovery(context)

    @Provides @Singleton
    fun monitor(
        session: BoardSession,
        store: BoardStore,
        events: RoomEventStore,
        syncer: EventSyncer,
        notifier: Notifier,
        discovery: BoardDiscovery,
        @AppScope scope: CoroutineScope,
    ): BoardMonitor = BoardMonitor(session, store, events, syncer, notifier, discovery, scope)

    @Provides @Singleton
    fun bleProvisioner(@ApplicationContext context: Context): BleBoardProvisioner = BleBoardProvisioner(context)

    @Provides @Singleton
    fun fakeProvisioner(): FakeBoardProvisioner = FakeBoardProvisioner()
}
