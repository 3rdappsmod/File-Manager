package org.fossify.filemanager.helpers.packages

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.util.concurrent.Executors

class PackageInstallModel(application: Application) : AndroidViewModel(application) {
    val state = MutableLiveData(InstallPreparation())
    private val sessions = PackageInstallSession(application)
    private val executor = Executors.newSingleThreadExecutor()
    private var started = false
    private var sessionId: Int? = null
    private var cleared = false

    fun prepare(uri: Uri) {
        if (started) return
        started = true
        executor.execute { prepareInBackground(uri) }
    }

    // Providers and the installer are external process boundaries; surface their errors to the user.
    @Suppress("TooGenericExceptionCaught")
    private fun prepareInBackground(uri: Uri) {
        try {
            val id = sessions.prepare(uri)
            synchronized(this) {
                if (cleared) sessions.abandon(id) else {
                    sessionId = id
                    state.postValue(InstallPreparation(ready = true))
                }
            }
        } catch (exception: Exception) {
            state.postValue(InstallPreparation(error = exception.message ?: exception.javaClass.simpleName))
        }
    }

    @Synchronized
    fun commit() {
        val id = checkNotNull(sessionId)
        sessions.commit(id)
        sessionId = null
    }

    @Synchronized
    override fun onCleared() {
        cleared = true
        executor.shutdownNow()
        sessionId?.let { sessions.abandon(it) }
        sessionId = null
    }
}

data class InstallPreparation(val ready: Boolean = false, val error: String? = null)
