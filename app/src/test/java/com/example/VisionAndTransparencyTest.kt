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
import com.example.vision.CameraVisionManager
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.File

@RunWith(RobolectricTestRunner::class)
class VisionAndTransparencyTest {

    private lateinit var context: Context
    private lateinit var db: HubDatabase
    private lateinit var blobDir: File
    private lateinit var vault: KeystoreVault
    private lateinit var historyRepository: HistoryRepository
    private lateinit var connectivityObserver: ConnectivityObserver
    private lateinit var liveManager: LiveSessionManager
    private lateinit var cameraManager: CameraVisionManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, HubDatabase::class.java).allowMainThreadQueries().build()
        blobDir = File(context.filesDir, "test_vision_blobs").apply { mkdirs() }
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

        cameraManager = CameraVisionManager(context)
    }

    @After
    fun tearDown() {
        liveManager.cleanup()
        cameraManager.shutdown()
        db.close()
        blobDir.deleteRecursively()
    }

    @Test
    fun testLiveManagerVisionStateSetters() {
        assertFalse("Câmera inativa inicialmente", liveManager.isCameraActive.value)
        assertFalse("Tela inativa inicialmente", liveManager.isScreenShareActive.value)

        liveManager.setCameraActive(true)
        assertTrue("Câmera deve estar ativa", liveManager.isCameraActive.value)

        liveManager.setScreenShareActive(true)
        assertTrue("Tela deve estar ativa", liveManager.isScreenShareActive.value)

        liveManager.setCameraActive(false)
        assertFalse("Câmera deve estar inativa", liveManager.isCameraActive.value)
    }

    @Test
    fun testCameraVisionManagerDefaults() {
        assertFalse("Câmera padrão deve ser traseira", cameraManager.isFrontCamera)
        assertEquals("FPS padrão deve ser 1.0", 1.0f, cameraManager.configuredFps, 0.01f)
        assertEquals("Qualidade JPEG padrão deve ser 70", 70, cameraManager.jpegQuality)
    }
}
