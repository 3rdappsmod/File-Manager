package org.fossify.filemanager.helpers.packages

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import java.io.InterruptedIOException
import java.util.concurrent.Executors

class AppBackupModel(application: Application) : AndroidViewModel(application) {
    val apps = MutableLiveData<List<BackupApp>>(emptyList())
    val state = MutableLiveData(BackupState(busy = true))
    val selected = mutableSetOf<String>()
    var system = false
    var query = ""
    private val executor = Executors.newSingleThreadExecutor()

    init {
        executor.execute { load() }
    }

    // Worker boundary: PackageManager and document providers can throw vendor-specific runtime exceptions.
    @Suppress("TooGenericExceptionCaught")
    private fun load() {
        try {
            apps.postValue(InstalledApps(getApplication<Application>().packageManager).list())
            state.postValue(BackupState())
        } catch (exception: Exception) {
            state.postValue(BackupState(errors = listOf(exception.message.orEmpty())))
        }
    }

    fun backup() {
        if (state.value?.busy == true) return
        val chosen = apps.value.orEmpty().filter { it.packageName in selected }
        if (chosen.isEmpty()) return
        state.value = BackupState(busy = true, total = chosen.size)
        executor.execute { save(chosen) }
    }

    @Suppress("TooGenericExceptionCaught")
    private fun save(chosen: List<BackupApp>) {
        val errors = mutableListOf<String>()
        var completed = 0
        for ((index, app) in chosen.withIndex()) {
            if (Thread.currentThread().isInterrupted) break
            state.postValue(BackupState(true, index, chosen.size, app.label))
            try {
                PackageBackup(getApplication()).save(app)
                completed++
            } catch (_: InterruptedIOException) {
                Thread.currentThread().interrupt()
            } catch (exception: Exception) {
                errors.add("${app.label}: ${exception.message.orEmpty()}")
            }
        }
        state.postValue(BackupState(completed = completed, total = chosen.size, errors = errors))
    }

    override fun onCleared() {
        executor.shutdownNow()
    }
}

data class BackupState(
    val busy: Boolean = false,
    val completed: Int = 0,
    val total: Int = 0,
    val current: String = "",
    val errors: List<String> = emptyList()
)
