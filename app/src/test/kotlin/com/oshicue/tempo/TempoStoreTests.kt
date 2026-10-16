package com.oshicue.tempo

import com.oshicue.tempo.core.TempoDemo
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.data.TempoStorageException
import com.oshicue.tempo.data.TempoStore
import java.io.File
import java.util.UUID
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TempoStoreTests {
    private fun temporaryFile(): File {
        val directory = File(System.getProperty("java.io.tmpdir"), "tempo-test-${UUID.randomUUID()}")
        directory.mkdirs()
        return File(directory, "state.json")
    }

    private val now = 1_800_000_000_000L

    @Test
    fun testPersistenceRestoresRunningAndPausedTimers() {
        val file = temporaryFile()
        val store = TempoStore(file)
        val group = store.groups.first()
        store.start(memberID = group.members[0].id, tickets = 2, now = now)
        store.start(memberID = group.members[1].id, tickets = 3, now = now)
        store.pause(memberID = group.members[1].id, now = now + 10_000)

        val restored = TempoStore(file)
        assertNull(restored.message)
        assertEquals(store.groups, restored.groups)
        assertEquals(store.rounds, restored.rounds)
        assertEquals(group.id, restored.selectedGroupID)
        assertEquals(
            10.0,
            restored.round(forMember = group.members[0].id)?.remaining(now + 50_000)
        )
        assertEquals(
            80.0,
            restored.round(forMember = group.members[1].id)?.remaining(now + 500_000)
        )
    }

    @Test
    fun testEditingGroupFreezesExistingRoundDuration() {
        val file = temporaryFile()
        val store = TempoStore(file)
        var group = store.groups.first()
        val memberID = group.members[0].id
        store.start(memberID = memberID, tickets = 2, now = now)
        group = group.copy(
            secondsPerTicket = 45,
            members = group.members.mapIndexed { index, member ->
                if (index == 0) member.copy(soundID = "arcade") else member
            }
        )
        store.saveGroup(group)
        val round = assertNotNull(store.round(forMember = memberID))
        assertEquals(30, round.secondsPerTicket)
        assertEquals(60.0, round.duration)
        assertEquals("chime", round.soundID)

        val restored = TempoStore(file)
        assertEquals(round, restored.round(forMember = memberID))

        store.start(memberID = memberID, tickets = 2, now = now)
        assertEquals(90.0, store.round(forMember = memberID)?.duration)
        assertEquals("arcade", store.round(forMember = memberID)?.soundID)
    }

    @Test
    fun testTimersAcrossGroupsStayIndependentAndFinishOnlyOnce() {
        val file = temporaryFile()
        val store = TempoStore(file)
        val first = store.groups.first()
        val second = TempoGroup(
            id = java.util.UUID.randomUUID().toString(),
            name = "NOVA",
            secondsPerTicket = 15,
            members = listOf(TempoMember(memberId(), "海", "79B8FF"))
        )
        store.saveGroup(second)
        val finishedIDs = mutableListOf<String>()
        store.onRoundFinished = { round, _ -> finishedIDs.add(round.memberID) }

        store.start(memberID = first.members[0].id, tickets = 1, now = now)
        store.start(memberID = second.members[0].id, tickets = 1, now = now)
        store.selectedGroupID = second.id
        store.tick(now + 16_000)
        store.tick(now + 17_000)
        assertEquals(listOf(second.members[0].id), finishedIDs)
        assertEquals(13.0, store.round(forMember = first.members[0].id)?.remaining(now + 17_000))

        store.tick(now + 31_000)
        assertEquals(setOf(first.members[0].id, second.members[0].id), finishedIDs.toSet())
        assertEquals(2, finishedIDs.size)
    }

    @Test
    fun testDeleteMemberAndGroupCancelOnlyTheirRounds() {
        val file = temporaryFile()
        val store = TempoStore(file)
        var group = store.groups.first()
        store.start(memberID = group.members[0].id, tickets = 1)
        store.start(memberID = group.members[1].id, tickets = 2)
        val removedID = group.members.first().id
        val cancelledRound = assertNotNull(store.round(forMember = removedID)).id
        group = group.copy(members = group.members.drop(1))
        val cancelled = mutableListOf<String>()
        store.onRoundCancelled = { cancelled.add(it) }
        store.saveGroup(group)
        assertEquals(listOf(cancelledRound), cancelled)
        assertNull(store.round(forMember = removedID))
        assertEquals(1, store.rounds.size)

        store.deleteGroup(group.id)
        assertTrue(store.groups.isEmpty())
        assertTrue(store.rounds.isEmpty())
        assertNull(store.selectedGroupID)
        assertEquals(2, cancelled.size)
    }

    @Test
    fun testInvalidStartPreservesExistingRound() {
        val file = temporaryFile()
        val store = TempoStore(file)
        val memberID = store.groups.first().members.first().id
        store.start(memberID = memberID, tickets = 1)
        val original = store.round(forMember = memberID)
        assertFailsWith<Exception> { store.start(memberID = memberID, tickets = 0) }
        assertEquals(original, store.round(forMember = memberID))
    }

    @Test
    fun testCorruptDataIsPreservedAndNeverOverwritten() {
        val file = temporaryFile()
        val original = "unreadable original data".toByteArray()
        file.writeBytes(original)
        val store = TempoStore(file)
        assertTrue(store.hasUnreadableSnapshot)
        assertNotNull(store.message)
        assertTrue(store.groups.isEmpty())
        assertFailsWith<Exception> { store.saveGroup(TempoDemo.groups[0]) }
        assertEquals(original.toList(), file.readBytes().toList())
    }

    @Test
    fun testFailedSaveDoesNotCommitNewGroup() {
        val file = temporaryFile()
        val store = TempoStore(file)
        val original = store.groups
        // Replace the parent folder with a file to simulate an unwritable storage path.
        file.parentFile!!.deleteRecursively()
        file.parentFile!!.writeText("blocking parent")
        val next = TempoGroup(
            id = java.util.UUID.randomUUID().toString(),
            name = "NOVA",
            members = listOf(TempoMember(memberId(), "海", "79B8FF"))
        )
        assertFailsWith<TempoStorageException> { store.saveGroup(next) }
        assertEquals(original, store.groups)
        assertNotNull(store.message)
    }

    @Test
    fun testSelectedGroupFallsBackAndValidates() {
        val file = temporaryFile()
        val store = TempoStore(file)
        val first = store.groups.first().id
        store.selectedGroupID = null
        assertNull(store.selectedGroupID)
        store.selectedGroupID = first
        assertEquals(first, store.selectedGroupID)
        store.selectedGroupID = "not-a-group"
        assertEquals(first, store.selectedGroupID)
        assertNotNull(store.message)
    }

    private fun memberId(): String = java.util.UUID.randomUUID().toString().uppercase()
}
