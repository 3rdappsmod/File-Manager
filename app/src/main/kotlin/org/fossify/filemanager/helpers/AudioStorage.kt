package org.fossify.filemanager.helpers

import android.content.Context
import android.media.MediaPlayer
import org.fossify.commons.extensions.getAndroidSAFFileItems
import org.fossify.commons.extensions.getAndroidSAFUri
import org.fossify.commons.extensions.getOTGFastDocumentFile
import org.fossify.commons.extensions.getOTGItems
import org.fossify.commons.extensions.getSomeDocumentFile
import org.fossify.commons.extensions.isPathOnOTG
import org.fossify.commons.extensions.isRestrictedSAFOnlyRoot
import org.fossify.commons.helpers.ensureBackgroundThread
import org.fossify.filemanager.extensions.config
import java.io.File
import java.io.IOException

class AudioStorage(private val context: Context) {
    fun loadPlaylist(path: String, callback: (Result<List<String>>) -> Unit) {
        ensureBackgroundThread {
            try {
                val parent = File(path).parent ?: throw IOException("Audio file has no parent directory")
                val showHidden = context.config.shouldShowHidden()
                val complete: (List<String>) -> Unit = { paths ->
                    callback(Result.success(buildAudioPlaylist(path, paths, showHidden)))
                }
                when {
                    context.isRestrictedSAFOnlyRoot(path) ->
                        context.getAndroidSAFFileItems(parent, showHidden, false) { items ->
                            complete(items.filterNot { it.isDirectory }.map { it.path })
                        }
                    context.isPathOnOTG(path) && context.config.OTGTreeUri.isNotEmpty() ->
                        context.getOTGItems(parent, showHidden, false) { items ->
                            complete(items.filterNot { it.isDirectory }.map { it.path })
                        }
                    else -> complete(File(parent).listFiles()?.filter { it.isFile }?.map { it.path }.orEmpty())
                }
            } catch (error: SecurityException) {
                callback(Result.failure(error))
            } catch (error: IOException) {
                callback(Result.failure(error))
            }
        }
    }

    fun setDataSource(player: MediaPlayer, path: String) {
        if (File(path).canRead()) {
            player.setDataSource(path)
        } else {
            val uri = when {
                context.isRestrictedSAFOnlyRoot(path) -> context.getAndroidSAFUri(path)
                context.isPathOnOTG(path) -> context.getOTGFastDocumentFile(path)?.uri
                else -> context.getSomeDocumentFile(path)?.uri
            } ?: throw IOException("No readable audio document")
            player.setDataSource(context, uri)
        }
    }
}
