package com.oshicue.tempo.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oshicue.tempo.core.CountdownPhase
import com.oshicue.tempo.core.CountdownRound
import com.oshicue.tempo.core.TempoDemo
import com.oshicue.tempo.core.TempoError
import com.oshicue.tempo.core.TempoException
import com.oshicue.tempo.core.TempoGroup
import com.oshicue.tempo.core.TempoJson
import com.oshicue.tempo.core.TempoMember
import com.oshicue.tempo.core.TempoSnapshot
import com.oshicue.tempo.core.validated
import com.oshicue.tempo.i18n.LocalizedMessage
import java.io.File

/** Raised when the snapshot cannot be read back or written; [storageKey] names the message. */
class TempoStorageException(val storageKey: String, val detail: String = "") : Exception(storageKey)

/**
 * Owns groups, rounds and the selected group. Every mutation persists the whole
 * snapshot first and only then updates the observable state, so a failed disk
 * write never leaves the UI ahead of the stored data.
 */
class TempoStore(
    val storageFile: File,
    seedGroups: List<TempoGroup> = TempoDemo.groups
) {
    var groups by mutableStateOf<List<TempoGroup>>(emptyList())
        private set
    var rounds by mutableStateOf<Map<String, CountdownRound>>(emptyMap())
        private set

    private var selectedGroupIDState by mutableStateOf<String?>(null)
    var selectedGroupID: String?
        get() = selectedGroupIDState
        set(value) {
            if (isApplyingSnapshot) return
            val previous = selectedGroupIDState
            try {
                if (value != null && groups.none { it.id == value }) {
                    throw TempoException(TempoError.CorruptSnapshot)
                }
                selectedGroupIDState = value
                persist(snapshot())
            } catch (error: Exception) {
                isApplyingSnapshot = true
                selectedGroupIDState = previous
                isApplyingSnapshot = false
                message = errorMessageOf(error)
            }
        }

    var message by mutableStateOf<LocalizedMessage?>(null)
    var hasUnreadableSnapshot by mutableStateOf(false)
        private set

    var onRoundStarted: ((CountdownRound, TempoMember) -> Unit)? = null
    var onRoundCancelled: ((String) -> Unit)? = null
    var onRoundFinished: ((CountdownRound, TempoMember) -> Unit)? = null

    private var isApplyingSnapshot = false

    init {
        if (storageFile.exists()) {
            try {
                val restored = TempoJson.decode(storageFile.readText()) ?: throw TempoException(TempoError.CorruptSnapshot)
                apply(restored)
            } catch (error: Exception) {
                // Preserve the only copy of unreadable user data. All future writes are
                // blocked until the original has been explicitly recovered elsewhere.
                hasUnreadableSnapshot = true
                message = LocalizedMessage.of("storage.readFailed", error.message ?: storageFile.name)
            }
        } else {
            try {
                val initial = TempoSnapshot(
                    groups = seedGroups, selectedGroupID = seedGroups.firstOrNull()?.id
                ).validated()
                persist(initial)
                apply(initial)
            } catch (error: Exception) {
                apply(TempoSnapshot(groups = seedGroups, selectedGroupID = seedGroups.firstOrNull()?.id))
                message = errorMessageOf(error)
            }
        }
    }

    val runningCount: Int
        get() = rounds.values.count { it.phase(System.currentTimeMillis()) == CountdownPhase.Running }
    val finishedCount: Int
        get() = rounds.values.count { it.phase(System.currentTimeMillis()) == CountdownPhase.Finished }

    fun group(id: String): TempoGroup? = groups.firstOrNull { it.id == id }

    fun member(id: String): TempoMember? =
        groups.asSequence().flatMap { it.members }.firstOrNull { it.id == id }

    fun round(forMember: String): CountdownRound? = rounds[forMember]

    @Throws(TempoException::class, TempoStorageException::class)
    fun saveGroup(group: TempoGroup) {
        try {
            val clean = group.validated()
            val otherIDs = groups.filter { it.id != clean.id }
                .flatMap { it.members }.map { it.id }.toSet()
            if (clean.members.any { it.id in otherIDs }) throw TempoException(TempoError.DuplicateMember)
            var next = snapshot()
            val index = next.groups.indexOfFirst { it.id == clean.id }
            next = if (index >= 0) {
                next.copy(groups = next.groups.toMutableList().also { it[index] = clean })
            } else {
                next.copy(groups = next.groups + clean)
            }
            val retainedIDs = clean.members.map { it.id }.toSet()
            val removed = next.rounds.filter { it.groupID == clean.id && it.memberID !in retainedIDs }
            next = next.copy(
                rounds = next.rounds.filterNot { it.groupID == clean.id && it.memberID !in retainedIDs }
            )
            if (next.selectedGroupID == null) next = next.copy(selectedGroupID = clean.id)
            commit(next)
            removed.forEach { onRoundCancelled?.invoke(it.id) }
        } catch (error: Exception) {
            recordFailure(error)
            throw error
        }
    }

    @Throws(TempoException::class, TempoStorageException::class)
    fun deleteGroup(id: String) {
        try {
            var next = snapshot()
            val removed = next.rounds.filter { it.groupID == id }
            next = next.copy(
                groups = next.groups.filterNot { it.id == id },
                rounds = next.rounds.filterNot { it.groupID == id }
            )
            if (next.selectedGroupID == id) {
                next = next.copy(selectedGroupID = next.groups.firstOrNull()?.id)
            }
            commit(next)
            removed.forEach { onRoundCancelled?.invoke(it.id) }
        } catch (error: Exception) {
            recordFailure(error)
            throw error
        }
    }

    @Throws(TempoException::class, TempoStorageException::class)
    fun start(memberID: String, tickets: Int, now: Long = System.currentTimeMillis()) {
        try {
            val group = groups.firstOrNull { it.members.any { member -> member.id == memberID } }
                ?: throw TempoException(TempoError.MemberNotFound)
            val member = group.members.first { it.id == memberID }
            val round = CountdownRound.start(
                groupID = group.id, memberID = memberID, tickets = tickets,
                secondsPerTicket = group.secondsPerTicket, soundID = member.soundID, now = now
            )
            var next = snapshot()
            val replaced = rounds[memberID]
            next = next.copy(rounds = next.rounds.filterNot { it.memberID == memberID } + round)
            commit(next)
            replaced?.let { onRoundCancelled?.invoke(it.id) }
            onRoundStarted?.invoke(round, member)
        } catch (error: Exception) {
            recordFailure(error)
            throw error
        }
    }

    fun pause(memberID: String, now: Long = System.currentTimeMillis()) {
        val round = rounds[memberID] ?: return
        if (round.deadline == null || round.completedAt != null) return
        val old = round
        val updated = round.paused(now)
        try {
            replace(updated)
            onRoundCancelled?.invoke(old.id)
            if (updated.completedAt != null) {
                member(memberID)?.let { onRoundFinished?.invoke(updated, it) }
            }
        } catch (error: Exception) {
            recordFailure(error)
        }
    }

    fun resume(memberID: String, now: Long = System.currentTimeMillis()) {
        val round = rounds[memberID] ?: return
        if (round.pausedRemaining == null) return
        val member = member(memberID) ?: return
        val updated = round.resumed(now)
        try {
            replace(updated)
            onRoundStarted?.invoke(updated, member)
        } catch (error: Exception) {
            recordFailure(error)
        }
    }

    fun reset(memberID: String) {
        val round = rounds[memberID] ?: return
        try {
            val next = snapshot().let { value ->
                value.copy(rounds = value.rounds.filterNot { it.memberID == memberID })
            }
            commit(next)
            onRoundCancelled?.invoke(round.id)
        } catch (error: Exception) {
            recordFailure(error)
        }
    }

    /** The finished state remains visible until another round starts or is reset. */
    fun acknowledge(memberID: String) {
        val round = rounds[memberID] ?: return
        onRoundCancelled?.invoke(round.id)
    }

    fun tick(now: Long = System.currentTimeMillis()) {
        var next = snapshot()
        val completed = mutableListOf<CountdownRound>()
        next = next.copy(rounds = next.rounds.map { round ->
            if (round.completedAt == null && round.deadline != null && round.deadline <= now) {
                val finished = round.finished(round.deadline)
                completed.add(finished)
                finished
            } else {
                round
            }
        })
        if (completed.isEmpty()) return
        try {
            commit(next)
        } catch (error: Exception) {
            // An unavailable disk must not replay the alarm on every display tick.
            apply(next)
            recordFailure(error)
        }
        completed.forEach { round ->
            member(round.memberID)?.let { onRoundFinished?.invoke(round, it) }
        }
    }

    private fun snapshot(): TempoSnapshot =
        TempoSnapshot(groups = groups, rounds = rounds.values.toList(), selectedGroupID = selectedGroupID)

    @Throws(TempoStorageException::class)
    private fun replace(round: CountdownRound) {
        val next = snapshot().let { value ->
            value.copy(rounds = value.rounds.filterNot { it.memberID == round.memberID } + round)
        }
        commit(next)
    }

    @Throws(TempoStorageException::class)
    private fun commit(snapshot: TempoSnapshot) {
        persist(snapshot)
        apply(snapshot)
    }

    private fun apply(snapshot: TempoSnapshot) {
        isApplyingSnapshot = true
        groups = snapshot.groups
        rounds = snapshot.rounds.associateBy { it.memberID }
        selectedGroupIDState = snapshot.selectedGroupID
        isApplyingSnapshot = false
    }

    @Throws(TempoStorageException::class)
    private fun persist(snapshot: TempoSnapshot) {
        if (hasUnreadableSnapshot) throw TempoStorageException("storage.unreadableSnapshot")
        try {
            TempoFiles.writeAtomic(storageFile, TempoJson.encode(snapshot).toByteArray(Charsets.UTF_8))
        } catch (error: Exception) {
            throw TempoStorageException("storage.writeFailed", error.message ?: error.toString())
        }
    }

    private fun recordFailure(error: Exception) {
        message = errorMessageOf(error)
    }

    private fun errorMessageOf(error: Exception): LocalizedMessage = when (error) {
        is TempoException -> LocalizedMessage.of(error.error.key)
        is TempoStorageException -> if (error.storageKey == "storage.writeFailed") {
            LocalizedMessage.of(error.storageKey, error.detail)
        } else {
            LocalizedMessage.of(error.storageKey)
        }
        else -> LocalizedMessage.of("storage.writeFailed", error.message ?: error.toString())
    }
}
