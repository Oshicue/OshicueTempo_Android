package com.oshicue.tempo.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

data class TempoSnapshot(
    val version: Int = 1,
    val groups: List<TempoGroup> = emptyList(),
    val rounds: List<CountdownRound> = emptyList(),
    val selectedGroupID: String? = null
) {
    fun validated(): TempoSnapshot {
        if (version != 1) throw TempoException(TempoError.CorruptSnapshot)
        if (groups.map { it.id }.toSet().size != groups.size) throw TempoException(TempoError.DuplicateGroup)
        val cleanGroups = groups.map { it.validated() }
        val members = cleanGroups.flatMap { it.members }
        if (members.map { it.id }.toSet().size != members.size) throw TempoException(TempoError.DuplicateMember)
        if (rounds.map { it.memberID }.toSet().size != rounds.size) throw TempoException(TempoError.CorruptSnapshot)
        if (rounds.map { it.id }.toSet().size != rounds.size) throw TempoException(TempoError.CorruptSnapshot)
        for (round in rounds) {
            val group = cleanGroups.firstOrNull { it.id == round.groupID }
                ?: throw TempoException(TempoError.CorruptSnapshot)
            if (group.members.none { it.id == round.memberID }) throw TempoException(TempoError.CorruptSnapshot)
            val expected = TempoLimits.duration(round.tickets, round.secondsPerTicket)
            if (round.duration != expected || !round.duration.isFinite()) {
                throw TempoException(TempoError.CorruptSnapshot)
            }
            if (round.soundID?.isEmpty() == true) throw TempoException(TempoError.CorruptSnapshot)
            val paused = round.pausedRemaining
            if (paused != null && (!paused.isFinite() || paused <= 0 || paused > round.duration)) {
                throw TempoException(TempoError.CorruptSnapshot)
            }
            if (paused != null && (round.deadline != null || round.completedAt != null)) {
                throw TempoException(TempoError.CorruptSnapshot)
            }
            if (round.completedAt != null && (round.deadline != null || round.pausedRemaining != null)) {
                throw TempoException(TempoError.CorruptSnapshot)
            }
        }
        val selected = if (cleanGroups.any { it.id == selectedGroupID }) {
            selectedGroupID
        } else {
            cleanGroups.firstOrNull()?.id
        }
        return copy(groups = cleanGroups, selectedGroupID = selected)
    }
}

/**
 * Reads and writes the same JSON document as the iOS app: sorted keys, two-space
 * pretty printing and epoch-millisecond dates. Existing iOS state.json files load
 * unchanged, and files written here can be read back by the iOS build.
 */
object TempoJson {
    private val parser = Json { isLenient = false }

    fun encode(snapshot: TempoSnapshot): String {
        val rootMap = sortedMapOf<String, JsonElement>(
            "groups" to JsonArray(snapshot.groups.map { encodeGroup(it) }),
            "rounds" to JsonArray(snapshot.rounds.map { encodeRound(it) }),
            "version" to JsonPrimitive(1)
        )
        snapshot.selectedGroupID?.let { rootMap["selectedGroupID"] = JsonPrimitive(it) }
        val builder = StringBuilder()
        write(builder, JsonObject(rootMap), 0)
        return builder.toString()
    }

    fun decode(text: String): TempoSnapshot? {
        val root = try {
            parser.parseToJsonElement(text) as? JsonObject
        } catch (_: Exception) {
            null
        } ?: return null
        return try {
            val version = root.intOrNull("version") ?: return null
            val groups = (root["groups"] as? JsonArray)?.mapNotNull { decodeGroup(it) } ?: return null
            if (groups.size != (root["groups"] as? JsonArray)?.size) return null
            val roundsArray = (root["rounds"] as? JsonArray) ?: return null
            val rounds = roundsArray.mapNotNull { decodeRound(it) }
            if (rounds.size != roundsArray.size) return null
            val selected = root.stringOrNull("selectedGroupID")
            TempoSnapshot(version, groups, rounds, selected).validated()
        } catch (_: TempoException) {
            null
        } catch (_: Exception) {
            null
        }
    }

    private fun encodeGroup(group: TempoGroup): JsonElement = JsonObject(
        sortedMapOf(
            "id" to JsonPrimitive(group.id),
            "members" to JsonArray(group.members.map { member ->
                JsonObject(
                    sortedMapOf(
                        "colorHex" to JsonPrimitive(member.colorHex),
                        "id" to JsonPrimitive(member.id),
                        "name" to JsonPrimitive(member.name),
                        "soundID" to JsonPrimitive(member.soundID)
                    )
                )
            }),
            "name" to JsonPrimitive(group.name),
            "secondsPerTicket" to JsonPrimitive(group.secondsPerTicket)
        )
    )

    private fun encodeRound(round: CountdownRound): JsonElement {
        val map = sortedMapOf<String, JsonElement>(
            "duration" to JsonPrimitive(round.duration),
            "groupID" to JsonPrimitive(round.groupID),
            "id" to JsonPrimitive(round.id),
            "memberID" to JsonPrimitive(round.memberID),
            "secondsPerTicket" to JsonPrimitive(round.secondsPerTicket),
            "tickets" to JsonPrimitive(round.tickets)
        )
        round.completedAt?.let { map["completedAt"] = JsonPrimitive(it) }
        round.deadline?.let { map["deadline"] = JsonPrimitive(it) }
        round.pausedRemaining?.let { map["pausedRemaining"] = JsonPrimitive(it) }
        round.soundID?.let { map["soundID"] = JsonPrimitive(it) }
        return JsonObject(map)
    }

    private fun decodeGroup(element: JsonElement): TempoGroup? {
        val objectValue = element as? JsonObject ?: return null
        val id = canonicalID(objectValue.stringOrNull("id")) ?: return null
        val name = objectValue.stringOrNull("name") ?: return null
        val seconds = objectValue.intOrNull("secondsPerTicket") ?: return null
        val membersArray = objectValue["members"] as? JsonArray ?: return null
        val members = membersArray.mapNotNull { decodeMember(it) }
        if (members.size != membersArray.size) return null
        return TempoGroup(id = id, name = name, secondsPerTicket = seconds, members = members)
    }

    private fun decodeMember(element: JsonElement): TempoMember? {
        val objectValue = element as? JsonObject ?: return null
        val id = canonicalID(objectValue.stringOrNull("id")) ?: return null
        val name = objectValue.stringOrNull("name") ?: return null
        val color = objectValue.stringOrNull("colorHex") ?: return null
        val sound = objectValue.stringOrNull("soundID") ?: return null
        return TempoMember(id = id, name = name, colorHex = color, soundID = sound)
    }

    private fun decodeRound(element: JsonElement): CountdownRound? {
        val objectValue = element as? JsonObject ?: return null
        val id = canonicalID(objectValue.stringOrNull("id")) ?: return null
        val groupID = canonicalID(objectValue.stringOrNull("groupID")) ?: return null
        val memberID = canonicalID(objectValue.stringOrNull("memberID")) ?: return null
        val tickets = objectValue.intOrNull("tickets") ?: return null
        val seconds = objectValue.intOrNull("secondsPerTicket") ?: return null
        val duration = objectValue.doubleOrNull("duration") ?: return null
        val soundID = objectValue.stringOrNull("soundID")
        val deadline = objectValue.longOrNull("deadline")
        val pausedRemaining = objectValue.doubleOrNull("pausedRemaining")
        val completedAt = objectValue.longOrNull("completedAt")
        return CountdownRound(
            id = id, groupID = groupID, memberID = memberID, tickets = tickets,
            secondsPerTicket = seconds, duration = duration, soundID = soundID,
            deadline = deadline, pausedRemaining = pausedRemaining, completedAt = completedAt
        )
    }

    private fun JsonObject.stringOrNull(key: String): String? {
        val value = this[key] as? JsonPrimitive ?: return null
        return if (value.isString) value.content else null
    }

    private fun JsonObject.intOrNull(key: String): Int? {
        val value = this[key] as? JsonPrimitive ?: return null
        return value.content.toIntOrNull()
    }

    private fun JsonObject.longOrNull(key: String): Long? {
        val value = this[key] as? JsonPrimitive ?: return null
        if (value.content.toLongOrNull() != null) return value.content.toLongOrNull()
        val double = value.content.toDoubleOrNull() ?: return null
        if (!double.isFinite()) return null
        return double.toLong()
    }

    private fun JsonObject.doubleOrNull(key: String): Double? {
        val value = this[key] as? JsonPrimitive ?: return null
        val double = value.content.toDoubleOrNull() ?: return null
        return if (double.isFinite()) double else null
    }

    private fun write(builder: StringBuilder, element: JsonElement, depth: Int) {
        when (element) {
            is JsonNull -> builder.append("null")
            is JsonPrimitive -> {
                if (element.isString) {
                    writeString(builder, element.content)
                } else {
                    builder.append(numberText(element.content))
                }
            }
            is JsonArray -> {
                if (element.isEmpty()) {
                    // Matches Foundation's pretty printer for empty containers.
                    builder.append("[\n\n")
                    indent(builder, depth)
                    builder.append("]")
                    return
                }
                builder.append("[\n")
                element.forEachIndexed { index, item ->
                    indent(builder, depth + 1)
                    write(builder, item, depth + 1)
                    if (index != element.size - 1) builder.append(",")
                    builder.append("\n")
                }
                indent(builder, depth)
                builder.append("]")
            }
            is JsonObject -> {
                if (element.isEmpty()) {
                    builder.append("{\n\n")
                    indent(builder, depth)
                    builder.append("}")
                    return
                }
                builder.append("{\n")
                val entries = element.entries.toList()
                entries.forEachIndexed { index, (key, value) ->
                    indent(builder, depth + 1)
                    writeString(builder, key)
                    builder.append(" : ")
                    write(builder, value, depth + 1)
                    if (index != entries.size - 1) builder.append(",")
                    builder.append("\n")
                }
                indent(builder, depth)
                builder.append("}")
            }
        }
    }

    /** Encodes integral doubles without a trailing decimal point, like the Swift encoder. */
    private fun numberText(content: String): String {
        val double = content.toDoubleOrNull() ?: return content
        return if (double.isFinite() && double == Math.floor(double) && Math.abs(double) < 1e15) {
            double.toLong().toString()
        } else {
            content
        }
    }

    private fun indent(builder: StringBuilder, depth: Int) {
        repeat(depth) { builder.append("  ") }
    }

    private fun writeString(builder: StringBuilder, value: String) {
        builder.append('"')
        for (character in value) {
            when (character) {
                '"' -> builder.append("\\\"")
                '\\' -> builder.append("\\\\")
                '\n' -> builder.append("\\n")
                '\r' -> builder.append("\\r")
                '\t' -> builder.append("\\t")
                '\b' -> builder.append("\\b")
                '\u000C' -> builder.append("\\f")
                else -> if (character < ' ') {
                    builder.append("\\u%04x".format(character.code))
                } else {
                    builder.append(character)
                }
            }
        }
        builder.append('"')
    }
}
