package org.fossify.gallery.helpers

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.fossify.gallery.extensions.mediaDB
import org.fossify.gallery.models.Medium
import java.util.LinkedHashMap
import java.util.LinkedHashSet
import java.util.Locale

/**
 * Batches writes to the existing Room media table so it can act as the gallery's last
 * successfully reconciled snapshot.  The UI never waits for this object: writes are copied
 * out of the pending queues and performed on a worker thread.
 */
object MediaSnapshotCoordinator {
    private const val FLUSH_DELAY = 150L
    private const val LIBRARY_SCOPE = "\u0000library"

    private val handler = Handler(Looper.getMainLooper())
    private val lock = Any()
    private var applicationContext: Context? = null
    private var flushScheduled = false
    private val pendingUpserts = LinkedHashMap<String, Medium>()
    private val pendingRemovals = LinkedHashSet<String>()
    private val pendingReplacements = LinkedHashMap<String, List<Medium>>()

    private val flushRunnable = Runnable {
        flushScheduled = false
        flushPending()
    }

    fun upsert(context: Context, media: Collection<Medium>) {
        if (media.isEmpty()) {
            return
        }

        synchronized(lock) {
            applicationContext = context.applicationContext
            media.forEach { medium ->
                val key = keyOf(medium.path)
                pendingRemovals.remove(key)
                pendingUpserts[key] = medium.copy()
            }
            scheduleFlushLocked()
        }
    }

    fun remove(context: Context, paths: Collection<String>) {
        if (paths.isEmpty()) {
            return
        }

        synchronized(lock) {
            applicationContext = context.applicationContext
            paths.forEach { path ->
                val key = keyOf(path)
                pendingUpserts.remove(key)
                pendingRemovals.add(key)
            }
            scheduleFlushLocked()
        }
    }

    /** Replace the active rows for one normal folder after an authoritative scan. */
    fun replaceFolder(context: Context, folder: String, media: Collection<Medium>) {
        replaceScope(context, folder, media)
    }

    /** Replace every active row after a Show All authoritative scan. */
    fun replaceLibrary(context: Context, media: Collection<Medium>) {
        replaceScope(context, LIBRARY_SCOPE, media)
    }

    fun flush(context: Context) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            handler.removeCallbacks(flushRunnable)
            flushScheduled = false
        }
        flushPending()
    }

    private fun replaceScope(context: Context, scope: String, media: Collection<Medium>) {
        synchronized(lock) {
            applicationContext = context.applicationContext
            val snapshot = media
                .asSequence()
                .filter { it.deletedTS == 0L && it.size > 0L }
                .distinctBy { keyOf(it.path) }
                .map { it.copy() }
                .toList()

            pendingReplacements[scope] = snapshot
            val snapshotKeys = snapshot.mapTo(HashSet()) { keyOf(it.path) }
            pendingRemovals.removeAll(snapshotKeys)
            snapshot.forEach { pendingUpserts.remove(keyOf(it.path)) }
            scheduleFlushLocked()
        }
    }

    private fun scheduleFlushLocked() {
        if (!flushScheduled) {
            flushScheduled = true
            handler.postDelayed(flushRunnable, FLUSH_DELAY)
        }
    }

    private fun flushPending() {
        val context: Context
        val upserts: List<Medium>
        val removals: List<String>
        val replacements: Map<String, List<Medium>>

        synchronized(lock) {
            context = applicationContext ?: return
            if (pendingUpserts.isEmpty() && pendingRemovals.isEmpty() && pendingReplacements.isEmpty()) {
                return
            }

            upserts = pendingUpserts.values.map { it.copy() }
            removals = pendingRemovals.toList()
            replacements = pendingReplacements.mapValues { (_, media) -> media.map { it.copy() } }
            pendingUpserts.clear()
            pendingRemovals.clear()
            pendingReplacements.clear()
        }

        Thread {
            try {
                replacements.forEach { (scope, media) ->
                    val existing = if (scope == LIBRARY_SCOPE) {
                        context.mediaDB.getCachedLibrary()
                    } else {
                        context.mediaDB.getMediaFromPath(scope)
                    }
                    val freshPaths = media.mapTo(HashSet()) { keyOf(it.path) }
                    existing
                        .filter { keyOf(it.path) !in freshPaths }
                        .forEach { context.mediaDB.deleteMediumPath(it.path) }
                    if (media.isNotEmpty()) {
                        context.mediaDB.insertAll(media)
                    }
                }

                removals.forEach { pathKey ->
                    // The DAO query is case-insensitive; using the key is safe for all normal
                    // Android paths and keeps duplicate notifications from producing duplicate work.
                    context.mediaDB.getCachedLibrary()
                        .firstOrNull { keyOf(it.path) == pathKey }
                        ?.let { context.mediaDB.deleteMediumPath(it.path) }
                }

                if (upserts.isNotEmpty()) {
                    context.mediaDB.insertAll(upserts)
                }
            } catch (ignored: Exception) {
                // A later authoritative scan will retry the snapshot write.
            }
        }.start()
    }

    private fun keyOf(path: String) = path.lowercase(Locale.getDefault())
}

/** Small, platform-free list merge used by incremental media updates and unit tests. */
object MediaSnapshotMerger {
    fun <T> merge(
        current: List<T>,
        upserts: Collection<T>,
        removals: Collection<String>,
        keyOf: (T) -> String
    ): ArrayList<T> {
        val removed = removals.mapTo(HashSet()) { it.lowercase(Locale.getDefault()) }
        val replacementByKey = LinkedHashMap<String, T>()
        upserts.forEach { replacementByKey[keyOf(it).lowercase(Locale.getDefault())] = it }

        val result = ArrayList<T>(current.size + upserts.size)
        val seen = HashSet<String>()
        current.forEach { item ->
            val key = keyOf(item).lowercase(Locale.getDefault())
            if (key in removed) {
                return@forEach
            }

            val replacement = replacementByKey.remove(key) ?: item
            if (seen.add(key)) {
                result.add(replacement)
            }
        }

        replacementByKey.forEach { (key, item) ->
            if (key !in removed && seen.add(key)) {
                result.add(item)
            }
        }
        return result
    }

    fun <T> rollback(current: List<T>, original: Collection<T>, failedKeys: Collection<String>, keyOf: (T) -> String): ArrayList<T> {
        val failed = failedKeys.mapTo(HashSet()) { it.lowercase(Locale.getDefault()) }
        return merge(current, original.filter { keyOf(it).lowercase(Locale.getDefault()) in failed }, emptyList(), keyOf)
    }
}
