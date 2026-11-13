package com.freecam.viewer.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.freecam.common.model.Pairing
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class CameraStoreTest {

    private lateinit var context: Context
    private lateinit var store: CameraStore

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext<Context>()
        store = CameraStore.getInstance(context)
        // Clear prefs
        val masterKeyAlias = "freecam_viewer_master_key"
        try {
            val masterKey = androidx.security.crypto.MasterKeys.getOrCreate(
                android.security.keystore.KeyGenParameterSpec.Builder(masterKeyAlias)
                    .setKeyScheme(android.security.keystore.KeyGenParameterSpec.KeyScheme.AES256_GCM)
                    .setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE)
                    .build()
            )
            val prefs = androidx.security.crypto.EncryptedSharedPreferences.create(
                "freecam_viewer_settings",
                masterKey,
                context,
                androidx.security.crypto.EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                androidx.security.crypto.EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            prefs.edit().clear().apply()
        } catch (e: Exception) {
            // Ignore
        }
    }

    @Test
    fun testAddAndGetCamera() {
        val camera = Pairing(
            name = "Test Camera",
            host = "100.64.1.1",
            token = "abcdef1234567890abcdef1234567890"
        )
        
        store.addCamera(camera)
        val cameras = store.getCameras()
        
        assertThat(cameras).hasSize(1)
        assertThat(cameras[0].name).isEqualTo("Test Camera")
        assertThat(cameras[0].host).isEqualTo("100.64.1.1")
        assertThat(cameras[0].token).isEqualTo("abcdef1234567890abcdef1234567890")
    }

    @Test
    fun testDuplicateCameraReplaced() {
        val camera1 = Pairing(
            name = "Camera 1",
            host = "100.64.1.1",
            token = "token123"
        )
        val camera2 = Pairing(
            name = "Camera 2 (Updated)",
            host = "100.64.1.1",
            token = "token123"
        )
        
        store.addCamera(camera1)
        store.addCamera(camera2)
        
        val cameras = store.getCameras()
        assertThat(cameras).hasSize(1)
        assertThat(cameras[0].name).isEqualTo("Camera 2 (Updated)")
    }

    @Test
    fun testRemoveCamera() {
        val camera1 = Pairing(name = "Cam 1", host = "100.64.1.1", token = "t1")
        val camera2 = Pairing(name = "Cam 2", host = "100.64.1.2", token = "t2")
        
        store.addCamera(camera1)
        store.addCamera(camera2)
        assertThat(store.getCameras()).hasSize(2)
        
        store.removeCamera("100.64.1.1", "t1")
        assertThat(store.getCameras()).hasSize(1)
        assertThat(store.getCameras()[0].name).isEqualTo("Cam 2")
    }

    @Test
    fun testUpdateCameraName() {
        val camera = Pairing(name = "Old Name", host = "100.64.1.1", token = "token123")
        store.addCamera(camera)
        
        store.updateCameraName("100.64.1.1", "token123", "New Name")
        
        val cameras = store.getCameras()
        assertThat(cameras).hasSize(1)
        assertThat(cameras[0].name).isEqualTo("New Name")
    }

    @Test
    fun testMultipleCameras() {
        for (i in 1..5) {
            store.addCamera(Pairing(
                name = "Camera $i",
                host = "100.64.1.$i",
                token = "token$i"
            ))
        }
        
        assertThat(store.getCameras()).hasSize(5)
    }
}