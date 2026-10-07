package com.rockbyte.vicu.repo

import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

internal class WorksRepository(private val store: WorksStore) : WorksRepo {
    // ponytail: application-wide Koin single; its scope lasts for the process.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    override val library: StateFlow<WorksLibraryState>
        field = MutableStateFlow(WorksLibraryState())

    init {
        scope.launch { store.changes.collect { requery() } }
        refresh()
    }

    override fun refresh() { scope.launch { requery() } }

    @Synchronized
    private fun requery() {
        try {
            library.value = WorksLibraryState(store.query(), loading = false)
        } catch (_: IOException) {
            library.value = library.value.copy(loading = false, failed = true)
        } catch (_: SecurityException) {
            library.value = library.value.copy(loading = false, failed = true)
        }
    }
}
