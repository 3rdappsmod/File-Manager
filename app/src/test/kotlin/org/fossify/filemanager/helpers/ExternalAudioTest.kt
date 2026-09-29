package org.fossify.filemanager.helpers

import java.io.File
import javax.xml.parsers.DocumentBuilderFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element

class ExternalAudioTest {
    @Test
    fun opaqueDocumentIsPlayableWithoutScanningOtherDocuments() {
        val source = "content://recorder.provider/recordings/123"
        assertEquals(listOf(source), buildAudioPlaylist(source, listOf("/storage/other.mp3"), false))
    }

    @Test
    fun explicitlyOpenedFileUriDoesNotDependOnExtensionOrHiddenPreference() {
        val source = "file:///storage/emulated/0/.recordings/recording"
        assertEquals(listOf(source), buildAudioPlaylist(source, emptyList(), false))
    }

    @Test
    fun localPathsAndNetworkUrlsAreNotExternalDocumentSources() {
        assertFalse(isExternalAudioUri("/storage/recording.mp3"))
        assertFalse(isExternalAudioUri("https://example.com/recording.mp3"))
        assertFalse(isExternalAudioUri("content-not-a-uri"))
    }

    @Test
    fun audioChooserEntryIsExportedButInternalPlayerRemainsPrivate() {
        val factory = DocumentBuilderFactory.newInstance().apply { isNamespaceAware = true }
        val document = factory.newDocumentBuilder().parse(File("src/main/AndroidManifest.xml"))
        val activities = document.getElementsByTagName("activity")
        val elements = (0 until activities.length).map { activities.item(it) as Element }
        val namespace = "http://schemas.android.com/apk/res/android"
        val gateway = elements.first { it.getAttributeNS(namespace, "name") == ".activities.OpenAudioActivity" }
        val player = elements.first { it.getAttributeNS(namespace, "name") == ".activities.MusicPlayerActivity" }
        assertEquals("true", gateway.getAttributeNS(namespace, "exported"))
        assertEquals("false", player.getAttributeNS(namespace, "exported"))
        val actions = gateway.getElementsByTagName("action")
        assertEquals("android.intent.action.VIEW", (actions.item(0) as Element).getAttributeNS(namespace, "name"))
        val data = gateway.getElementsByTagName("data")
        val types = (0 until data.length).map { (data.item(it) as Element).getAttributeNS(namespace, "mimeType") }
        assertTrue("audio/*" in types)
    }
}
