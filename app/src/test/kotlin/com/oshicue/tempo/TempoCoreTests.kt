package com.oshicue.tempo

import com.oshicue.tempo.core.CountdownPhase
import com.oshicue.tempo.core.CountdownRound
import com.oshicue.tempo.core.TempoDemo
import com.oshicue.tempo.core.TempoError
import com.oshicue.tempo.core.TempoException
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoJson
import com.oshicue.tempo.core.TempoLimits
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.core.TempoSnapshot
import com.oshicue.tempo.core.newID
import com.oshicue.tempo.core.validated
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class TempoCoreTests {
    private val start = 1_800_000_000_000L
    private fun at(seconds: Double) = start + (seconds * 1000).toLong()

    @Test
    fun testDeadlineUsesElapsedTimeAndClampsAtZero() {
        val round = CountdownRound.start(
            groupID = newID(), memberID = newID(), tickets = 3, secondsPerTicket = 30, now = start
        )
        assertEquals(90.0, round.duration)
        assertEquals(77.5, round.remaining(at(12.5)))
        assertEquals(0.0, round.remaining(at(95.0)))
        assertEquals(CountdownPhase.Finished, round.phase(at(95.0)))
        assertEquals(90.0, round.remaining(at(-20.0)))
    }

    @Test
    fun testPauseFreezesAndResumeCreatesANewDeadline() {
        var round = CountdownRound.start(
            groupID = newID(), memberID = newID(), tickets = 2, secondsPerTicket = 30, now = start
        )
        round = round.paused(at(10.0))
        assertEquals(CountdownPhase.Paused, round.phase(at(1000.0)))
        assertEquals(50.0, round.remaining(at(1000.0)))
        assertNull(round.deadline)
        round = round.resumed(at(100.0))
        assertEquals(at(150.0), round.deadline)
        assertNull(round.pausedRemaining)
        assertEquals(10.0, round.remaining(at(140.0)))
    }

    @Test
    fun testPausingAnExpiredTimerFinishesInsteadOfCreatingZeroLengthPause() {
        var round = CountdownRound.start(
            groupID = newID(), memberID = newID(), tickets = 1, secondsPerTicket = 10, now = start
        )
        round = round.paused(at(20.0))
        assertEquals(at(10.0), round.completedAt)
        assertEquals(CountdownPhase.Finished, round.phase(at(20.0)))
        assertNull(round.pausedRemaining)
    }

    @Test
    fun testTicketSecondsAndDailyDurationLimits() {
        for (tickets in listOf(-1, 0, 1000, Int.MAX_VALUE)) {
            assertFailsWith<TempoException> { TempoLimits.duration(tickets, 30) }
        }
        for (seconds in listOf(-1, 0, 3601, Int.MAX_VALUE)) {
            assertFailsWith<TempoException> { TempoLimits.duration(1, seconds) }
        }
        assertEquals(86_400.0, TempoLimits.duration(24, 3600))
        val tooLong = assertFailsWith<TempoException> { TempoLimits.duration(25, 3600) }
        assertEquals(TempoError.DurationTooLong, tooLong.error)
        assertEquals(999.0, TempoLimits.duration(999, 1))
    }

    @Test
    fun testGroupValidationNormalizesNamesAndColors() {
        val group = TempoGroup(
            id = newID(), name = "  LUMINA\n", secondsPerTicket = 30,
            members = listOf(TempoMember(newID(), " 小葵 ", "#ff688d"))
        )
        val clean = group.validated()
        assertEquals("LUMINA", clean.name)
        assertEquals("小葵", clean.members[0].name)
        assertEquals("FF688D", clean.members[0].colorHex)

        assertFailsWith<TempoException> {
            group.copy(members = listOf(group.members[0].copy(colorHex = "FF"))).validated()
        }
        assertFailsWith<TempoException> {
            group.copy(members = listOf(group.members[0].copy(name = " \n "))).validated()
        }
        assertFailsWith<TempoException> {
            group.copy(members = emptyList()).validated()
        }
    }

    @Test
    fun testSnapshotRoundTripsRunningAndPausedRounds() {
        val group = TempoDemo.groups[0]
        val running = CountdownRound.start(
            groupID = group.id, memberID = group.members[0].id, tickets = 2, secondsPerTicket = 30, now = start
        )
        val paused = CountdownRound.start(
            groupID = group.id, memberID = group.members[1].id, tickets = 1, secondsPerTicket = 30, now = start
        ).paused(at(4.0))
        val snapshot = TempoSnapshot(
            groups = listOf(group), rounds = listOf(running, paused), selectedGroupID = group.id
        )
        val encoded = TempoJson.encode(snapshot)
        val decoded = TempoJson.decode(encoded)!!
        assertEquals(snapshot, decoded)
        assertEquals(20.0, decoded.rounds[0].remaining(at(40.0)))
        assertEquals(26.0, decoded.rounds[1].remaining(at(1000.0)))
    }

    @Test
    fun testSnapshotRejectsOrphanedAndConflictingRounds() {
        val group = TempoDemo.groups[0]
        var round = CountdownRound.start(
            groupID = group.id, memberID = newID(), tickets = 1, secondsPerTicket = 30, now = start
        )
        assertFailsWith<TempoException> {
            TempoSnapshot(groups = listOf(group), rounds = listOf(round)).validated()
        }
        round = round.copy(memberID = group.members[0].id, pausedRemaining = 20.0)
        assertFailsWith<TempoException> {
            TempoSnapshot(groups = listOf(group), rounds = listOf(round)).validated()
        }
        round = round.copy(pausedRemaining = null, duration = 31.0)
        assertFailsWith<TempoException> {
            TempoSnapshot(groups = listOf(group), rounds = listOf(round)).validated()
        }
        assertNull(TempoJson.decode(TempoJson.encode(TempoSnapshot(groups = listOf(group), rounds = listOf(round)))))
    }

    @Test
    fun testEncodedJsonMatchesTheIosDocument() {
        val group = TempoDemo.groups[0]
        val snapshot = TempoSnapshot(groups = listOf(group), selectedGroupID = group.id)
        val encoded = TempoJson.encode(snapshot)
        // Keys sorted, two-space indentation and an unescaped solidus, like Swift's encoder.
        assertEquals(
            """
            {
              "groups" : [
                {
                  "id" : "12BB2EA5-EAA5-48D3-99DB-1183EE8EA901",
                  "members" : [
                    {
                      "colorHex" : "FF688D",
                      "id" : "726462D2-4B80-4CD1-9D01-80D7C910A001",
                      "name" : "小葵",
                      "soundID" : "chime"
                    },
                    {
                      "colorHex" : "79B8FF",
                      "id" : "726462D2-4B80-4CD1-9D01-80D7C910A002",
                      "name" : "凛",
                      "soundID" : "bell"
                    },
                    {
                      "colorHex" : "C4A2FF",
                      "id" : "726462D2-4B80-4CD1-9D01-80D7C910A003",
                      "name" : "诗音",
                      "soundID" : "pulse"
                    },
                    {
                      "colorHex" : "F7CB6B",
                      "id" : "726462D2-4B80-4CD1-9D01-80D7C910A004",
                      "name" : "柚月",
                      "soundID" : "arcade"
                    }
                  ],
                  "name" : "LUMINA",
                  "secondsPerTicket" : 30
                }
              ],
              "rounds" : [

              ],
              "selectedGroupID" : "12BB2EA5-EAA5-48D3-99DB-1183EE8EA901",
              "version" : 1
            }
            """.trimIndent(),
            encoded
        )
    }
}
