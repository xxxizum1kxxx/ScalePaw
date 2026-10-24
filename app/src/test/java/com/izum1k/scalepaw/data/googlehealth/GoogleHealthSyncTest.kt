package com.izum1k.scalepaw.data.googlehealth

import com.izum1k.scalepaw.domain.HealthPoint
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleHealthSyncTest {
    private class FakeRemote : HealthRemote {
        val stored = mutableMapOf<String, String>()
        val calls = mutableListOf<String>()
        var failOn: String? = null

        override suspend fun create(point: HealthPoint): Boolean {
            calls += "create ${point.id}"
            if (point.id == failOn) throw GoogleHealthException(GoogleHealthError.SERVER)
            if (point.id in stored) return false
            stored[point.id] = point.body
            return true
        }

        override suspend fun patch(point: HealthPoint): Boolean {
            calls += "patch ${point.id}"
            if (point.id !in stored) return false
            stored[point.id] = point.body
            return true
        }

        override suspend fun delete(dataType: String, ids: List<String>) {
            calls += "delete $dataType ${ids.sorted()}"
            ids.forEach(stored::remove)
        }
    }

    private class MemoryLedger : HealthLedger {
        var entries: Map<String, LedgerEntry> = emptyMap()
        override fun load() = entries
        override fun save(entries: Map<String, LedgerEntry>) {
            this.entries = entries.toMap()
        }
    }

    private val remote = FakeRemote()
    private val ledger = MemoryLedger()
    private val reconciler = HealthReconciler(remote, ledger)

    private fun weight(id: Long, grams: Int) = HealthPoint("sp-$id-w", "weight", """{"weight":{"weightGrams":$grams}}""")

    @Test
    fun should_create_when_point_is_new() = runTest {
        val outcome = reconciler.reconcile(listOf(weight(1, 80000)))
        assertEquals(listOf("create sp-1-w"), remote.calls)
        assertEquals(1, outcome.created)
        assertTrue("sp-1-w" in ledger.entries)
    }

    @Test
    fun should_patch_when_create_reports_existing() = runTest {
        remote.stored["sp-1-w"] = "old"
        val outcome = reconciler.reconcile(listOf(weight(1, 80000)))
        assertEquals(listOf("create sp-1-w", "patch sp-1-w"), remote.calls)
        assertEquals(1, outcome.updated)
        assertEquals(weight(1, 80000).body, remote.stored["sp-1-w"])
    }

    @Test
    fun should_skip_when_point_unchanged() = runTest {
        reconciler.reconcile(listOf(weight(1, 80000)))
        remote.calls.clear()
        reconciler.reconcile(listOf(weight(1, 80000)))
        assertTrue(remote.calls.isEmpty())
    }

    @Test
    fun should_patch_when_value_changed() = runTest {
        reconciler.reconcile(listOf(weight(1, 80000)))
        remote.calls.clear()
        reconciler.reconcile(listOf(weight(1, 81000)))
        assertEquals(listOf("patch sp-1-w"), remote.calls)
    }

    @Test
    fun should_batch_delete_when_point_removed_locally() = runTest {
        reconciler.reconcile(listOf(weight(1, 80000), weight(2, 81000), weight(3, 82000)))
        remote.calls.clear()
        val outcome = reconciler.reconcile(listOf(weight(2, 81000)))
        assertEquals(listOf("delete weight [sp-1-w, sp-3-w]"), remote.calls)
        assertEquals(2, outcome.deleted)
        assertEquals(setOf("sp-2-w"), ledger.entries.keys)
    }

    @Test
    fun should_keep_progress_when_sync_fails_midway() = runTest {
        remote.failOn = "sp-2-w"
        runCatching { reconciler.reconcile(listOf(weight(1, 80000), weight(2, 81000))) }
        assertEquals(setOf("sp-1-w"), ledger.entries.keys)
    }
}
