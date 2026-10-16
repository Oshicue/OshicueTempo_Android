package com.oshicue.tempo.core

/** Editable fictional group that ships with the app, matching the iOS demo data. */
object TempoDemo {
    val groups: List<TempoGroup> = listOf(
        TempoGroup(
            id = "12BB2EA5-EAA5-48D3-99DB-1183EE8EA901",
            name = "LUMINA",
            secondsPerTicket = 30,
            members = listOf(
                TempoMember("726462D2-4B80-4CD1-9D01-80D7C910A001", "小葵", "FF688D", "chime"),
                TempoMember("726462D2-4B80-4CD1-9D01-80D7C910A002", "凛", "79B8FF", "bell"),
                TempoMember("726462D2-4B80-4CD1-9D01-80D7C910A003", "诗音", "C4A2FF", "pulse"),
                TempoMember("726462D2-4B80-4CD1-9D01-80D7C910A004", "柚月", "F7CB6B", "arcade")
            )
        )
    )
}
