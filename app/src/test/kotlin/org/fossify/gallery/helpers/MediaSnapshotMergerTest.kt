package org.fossify.gallery.helpers

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaSnapshotMergerTest {
    private data class Item(val path: String, val modified: Long)

    @Test
    fun mergeDeduplicatesAndReplacesWithoutChangingUnrelatedOrder() {
        val current = listOf(
            Item("/Pictures/old.jpg", 1),
            Item("/Pictures/keep.jpg", 2),
            Item("/Pictures/duplicate.jpg", 3),
            Item("/Pictures/duplicate.jpg", 4)
        )

        val merged = MediaSnapshotMerger.merge(
            current = current,
            upserts = listOf(
                Item("/pictures/OLD.jpg", 10),
                Item("/Pictures/new.jpg", 11),
                Item("/Pictures/new.jpg", 12)
            ),
            removals = listOf("/Pictures/duplicate.jpg"),
            keyOf = { it.path }
        )

        assertEquals(
            listOf(
                Item("/pictures/OLD.jpg", 10),
                Item("/Pictures/keep.jpg", 2),
                Item("/Pictures/new.jpg", 12)
            ),
            merged
        )
    }

    @Test
    fun newestFirstSortingRemainsDeterministicAfterIncrementalMerge() {
        val merged = MediaSnapshotMerger.merge(
            current = listOf(Item("old.jpg", 100), Item("new.jpg", 300)),
            upserts = listOf(Item("middle.jpg", 200)),
            removals = emptyList(),
            keyOf = { it.path }
        )

        assertEquals(listOf("new.jpg", "middle.jpg", "old.jpg"), merged.sortedByDescending { it.modified }.map { it.path })
    }

    @Test
    fun rollbackRestoresOnlyFailedOperations() {
        val original = listOf(Item("one.jpg", 1), Item("two.jpg", 2), Item("three.jpg", 3))
        val optimistic = listOf(Item("two.jpg", 20))

        val rolledBack = MediaSnapshotMerger.rollback(
            current = optimistic,
            original = original,
            failedKeys = listOf("one.jpg", "three.jpg"),
            keyOf = { it.path }
        )

        assertEquals(
            listOf(Item("two.jpg", 20), Item("one.jpg", 1), Item("three.jpg", 3)),
            rolledBack
        )
    }

    @Test
    fun scrollAnchorCanBeFoundAfterRowsAreInsertedBeforeIt() {
        val before = listOf("new.jpg", "anchor.jpg", "old.jpg")
        val after = listOf("latest.jpg", "new.jpg", "anchor.jpg", "old.jpg")
        val anchor = before[1]

        assertEquals(2, after.indexOfFirst { it.equals(anchor, true) })
    }
}
