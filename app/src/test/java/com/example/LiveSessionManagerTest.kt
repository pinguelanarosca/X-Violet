package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.history.HistoryRepository
import com.example.live.LiveSessionManager
import com.example.network.ConnectivityObserver
import com.example.security.KeystoreVault
import com.example.security.VaultBlobStorage
import com.example.storage.HubDatabase
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class LiveSessionManagerTest {

    private lateinit var db: HubDatabase
    private lateinit var blobDir: File
    private lateinit var vault: KeystoreVault
    private lateinit var historyRepository: HistoryRepository
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var liveManager: LiveSessionManager

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        blobDir = File(context.filesDir, "test_live_blobs").apply { mkdirs() }
        vault = KeystoreVault(
            vaultDao = db.vaultDao(),
            blobStorage = VaultBlobStorage(blobDir),
            cryptoEngine = TestCryptoEngine()
        )
        historyRepository = HistoryRepository(db.conversationDao(), db.messageDao())
        connectivityObserver = ConnectivityObserver(context)

        liveManager = LiveSessionManager(
            context = context,
            keystoreVault = vault,
            historyRepository = historyRepository,
            connectivityObserver = connectivityObserver
        )
    }

    @After
    fun tearDown() {
        liveManager.cleanup()
        db.close()
        blobDir.deleteRecursively()
    }

    @Test
    fun testInitialStateIsIdle() = runBlocking {
        val state = liveManager.sessionState.value
        assertTrue("Estado inicial deve ser Idle", state is LiveSessionManager.SessionState.Idle)
        assertFalse("Áudio inativo inicialmente", liveManager.isAudioActive.value)
    }

    @Test
    fun testStartLiveSessionFailsIfNoKey() = runBlocking {
        liveManager.startLiveSession()
        kotlinx.coroutines.delay(500)

        val state = liveManager.sessionState.value
        assertTrue("Deveria falhar sem chave de API no cofre", state is LiveSessionManager.SessionState.Failed)
    }

    @Test
    fun testToggleProactiveAudioMute() {
        assertFalse(liveManager.isProactiveAudioMuted.value)
        liveManager.toggleProactiveAudioMute()
        assertTrue(liveManager.isProactiveAudioMuted.value)
        liveManager.toggleProactiveAudioMute()
        assertFalse(liveManager.isProactiveAudioMuted.value)
    }

    @Test
    fun testStopLiveSessionTransitionsToDisconnected() = runBlocking {
        liveManager.stopLiveSession()
        val state = liveManager.sessionState.value
        assertTrue("Deveria estar em Disconnected", state is LiveSessionManager.SessionState.Disconnected)
    }

    @Test
    fun testCleanupIsIdempotent() {
        liveManager.cleanup()
        liveManager.cleanup() // Second call must not crash
        assertFalse(liveManager.isAudioActive.value)
    }
}
