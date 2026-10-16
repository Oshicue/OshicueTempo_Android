package com.oshicue.tempo.core

import com.oshicue.tempo.i18n.L10n
import java.util.UUID

/** Mirrors the iOS TempoMember. IDs travel as uppercase UUID strings. */
data class TempoMember(
    val id: String,
    val name: String,
    val colorHex: String,
    val soundID: String = "chime"
) {
    companion object {
        fun create(name: String, colorHex: String, soundID: String = "chime"): TempoMember =
            TempoMember(newID(), name, colorHex, soundID)
    }
}

data class TempoGroup(
    val id: String,
    val name: String,
    val secondsPerTicket: Int = 30,
    val members: List<TempoMember> = emptyList()
) {
    companion object {
        fun create(
            name: String,
            secondsPerTicket: Int = 30,
            members: List<TempoMember> = emptyList()
        ): TempoGroup = TempoGroup(newID(), name, secondsPerTicket, members)
    }
}

enum class CountdownPhase { Idle, Running, Paused, Finished }

/**
 * A round keeps its original ticket price in seconds even when its group is edited.
 * Running timers use an absolute deadline, so backgrounding never loses elapsed time.
 * Times are epoch milliseconds, matching the iOS snapshot's millisecondsSince1970 dates.
 */
data class CountdownRound(
    val id: String,
    val groupID: String,
    val memberID: String,
    val tickets: Int,
    val secondsPerTicket: Int,
    val duration: Double,
    val soundID: String? = null,
    val deadline: Long? = null,
    val pausedRemaining: Double? = null,
    val completedAt: Long? = null
) {
    fun remaining(now: Long): Double {
        if (completedAt != null) return 0.0
        pausedRemaining?.let { return minOf(duration, maxOf(0.0, it)) }
        deadline?.let { return minOf(duration, maxOf(0.0, (it - now) / 1000.0)) }
        return duration
    }

    fun phase(now: Long): CountdownPhase = when {
        completedAt != null -> CountdownPhase.Finished
        pausedRemaining != null -> CountdownPhase.Paused
        deadline != null -> if (remaining(now) > 0) CountdownPhase.Running else CountdownPhase.Finished
        else -> CountdownPhase.Idle
    }

    fun paused(now: Long): CountdownRound {
        val deadline = deadline ?: return this
        if (completedAt != null) return this
        val left = remaining(now)
        return if (left <= 0) {
            finished(deadline)
        } else {
            copy(pausedRemaining = left, deadline = null)
        }
    }

    fun resumed(now: Long): CountdownRound {
        val left = pausedRemaining ?: return this
        if (completedAt != null) return this
        return copy(deadline = now + (left * 1000).toLong(), pausedRemaining = null)
    }

    fun finished(now: Long): CountdownRound =
        copy(completedAt = now, deadline = null, pausedRemaining = null)

    companion object {
        fun start(
            groupID: String,
            memberID: String,
            tickets: Int,
            secondsPerTicket: Int,
            soundID: String? = null,
            now: Long
        ): CountdownRound {
            val duration = TempoLimits.duration(tickets, secondsPerTicket)
            return CountdownRound(
                id = newID(), groupID = groupID, memberID = memberID, tickets = tickets,
                secondsPerTicket = secondsPerTicket, duration = duration,
                soundID = soundID, deadline = now + (duration * 1000).toLong()
            )
        }
    }
}

object TempoLimits {
    val tickets = 1..999
    val secondsPerTicket = 1..3600
    const val MAXIMUM_DURATION: Double = 24.0 * 60 * 60

    fun duration(tickets: Int, secondsPerTicket: Int): Double {
        if (tickets !in this.tickets) throw TempoException(TempoError.InvalidTickets)
        if (secondsPerTicket !in this.secondsPerTicket) throw TempoException(TempoError.InvalidSecondsPerTicket)
        val seconds = tickets.toDouble() * secondsPerTicket.toDouble()
        if (seconds > MAXIMUM_DURATION) throw TempoException(TempoError.DurationTooLong)
        return seconds
    }
}

/** Validation and storage errors that mirror the iOS TempoValidationError cases. */
enum class TempoError(val key: String) {
    EmptyGroupName("validation.emptyGroupName"),
    EmptyMemberName("validation.emptyMemberName"),
    EmptyMembers("validation.emptyMembers"),
    InvalidColor("validation.invalidColor"),
    InvalidSound("validation.invalidSound"),
    InvalidTickets("validation.invalidTickets"),
    InvalidSecondsPerTicket("validation.invalidSecondsPerTicket"),
    DurationTooLong("validation.durationTooLong"),
    DuplicateMember("validation.duplicateMember"),
    DuplicateGroup("validation.duplicateGroup"),
    MemberNotFound("validation.memberNotFound"),
    CorruptSnapshot("validation.corruptSnapshot");

    val message: String get() = L10n.text(key)
}

class TempoException(val error: TempoError) : Exception(error.message)

fun TempoGroup.validated(): TempoGroup {
    val cleanName = name.trim()
    if (cleanName.isEmpty()) throw TempoException(TempoError.EmptyGroupName)
    if (secondsPerTicket !in TempoLimits.secondsPerTicket) throw TempoException(TempoError.InvalidSecondsPerTicket)
    if (members.isEmpty()) throw TempoException(TempoError.EmptyMembers)
    if (members.map { it.id }.toSet().size != members.size) throw TempoException(TempoError.DuplicateMember)
    val cleanMembers = members.map { member ->
        val memberName = member.name.trim()
        if (memberName.isEmpty()) throw TempoException(TempoError.EmptyMemberName)
        val hex = member.colorHex.trim().trimStart('#')
        if (hex.length != 6 || !hex.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            throw TempoException(TempoError.InvalidColor)
        }
        if (member.soundID.isEmpty()) throw TempoException(TempoError.InvalidSound)
        member.copy(name = memberName, colorHex = hex.uppercase())
    }
    return copy(name = cleanName, members = cleanMembers)
}

fun newID(): String = UUID.randomUUID().toString().uppercase()

/** Canonicalizes an identifier the way Swift's UUID decoding does, or null when malformed. */
fun canonicalID(value: String?): String? {
    if (value == null) return null
    return try {
        UUID.fromString(value).toString().uppercase()
    } catch (_: IllegalArgumentException) {
        null
    }
}
