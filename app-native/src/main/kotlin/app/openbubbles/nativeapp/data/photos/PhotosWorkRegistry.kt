package app.openbubbles.nativeapp.data.photos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

/**
 * Process-wide ownership for account-bound Photos work. Sign-out must await
 * [cancelAndJoinAll] before clearing the catalog and private cache roots, so a
 * retained navigation ViewModel cannot repopulate the previous account's data.
 */
internal object PhotosWorkRegistry {
    private val lock = Any()
    private val sessions = mutableSetOf<PhotosWorkSession>()
    private var active = false

    fun register(): PhotosWorkSession {
        lateinit var session: PhotosWorkSession
        synchronized(lock) {
            session = PhotosWorkSession(
                initiallyInvalidated = !active,
                onClosed = { synchronized(lock) { sessions.remove(session) } },
            )
            if (active) sessions += session
        }
        return session
    }

    /** Called only when a connected Apple service state owns a new account session. */
    fun activate() {
        synchronized(lock) { active = true }
    }

    suspend fun cancelAndJoinAll() {
        val snapshot = synchronized(lock) {
            active = false
            sessions.toList()
        }
        val jobs = snapshot.flatMap(PhotosWorkSession::invalidate)
        jobs.forEach { it.cancel() }
        jobs.joinAll()
    }
}

/** One ViewModel generation. An invalidated session can never launch again. */
internal class PhotosWorkSession(
    initiallyInvalidated: Boolean,
    private val onClosed: () -> Unit,
) {
    private val lock = Any()
    private val jobs = mutableSetOf<Job>()
    private var invalidated = initiallyInvalidated

    /**
     * Returns a tracked lazy job so callers can publish it in their own job
     * maps before starting, avoiding fast-completion bookkeeping races.
     */
    fun createJob(
        scope: CoroutineScope,
        block: suspend CoroutineScope.() -> Unit,
    ): Job? {
        lateinit var job: Job
        job = scope.launch(start = CoroutineStart.LAZY) {
            try {
                block()
            } finally {
                synchronized(lock) { jobs.remove(job) }
            }
        }
        return synchronized(lock) {
            if (invalidated) {
                job.cancel()
                null
            } else {
                jobs += job
                job
            }
        }
    }

    /**
     * Adopt a job owned elsewhere, such as WorkManager's CoroutineWorker.
     * Cancellation must reach that real worker; wrapping it in a detached job
     * would let account cleanup finish while the underlying upload survives.
     */
    fun adopt(job: Job): Boolean = synchronized(lock) {
        if (invalidated || !job.isActive) {
            false
        } else {
            jobs += job
            job.invokeOnCompletion {
                synchronized(lock) { jobs.remove(job) }
            }
            true
        }
    }

    /** Release an externally owned job before closing its temporary session. */
    fun release(job: Job) {
        synchronized(lock) { jobs.remove(job) }
    }

    internal fun invalidate(): List<Job> {
        val snapshot = synchronized(lock) {
            invalidated = true
            jobs.toList().also { jobs.clear() }
        }
        onClosed()
        return snapshot
    }

    fun close() {
        invalidate().forEach { it.cancel() }
    }
}
