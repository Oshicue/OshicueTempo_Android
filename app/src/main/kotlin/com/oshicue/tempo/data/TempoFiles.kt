package com.oshicue.tempo.data

import android.content.Context
import java.io.File
import java.util.UUID

internal object TempoFiles {
    fun directory(context: Context): File {
        val directory = File(context.filesDir, "OshicueTempo")
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    fun soundsDirectory(context: Context): File {
        val directory = File(directory(context), "Sounds")
        if (!directory.exists()) directory.mkdirs()
        return directory
    }

    /** Writes through a temporary file so an interrupted write cannot truncate the original. */
    fun writeAtomic(file: File, bytes: ByteArray) {
        file.parentFile?.mkdirs()
        val temporary = File(file.parentFile, file.name + ".tmp")
        temporary.writeBytes(bytes)
        if (!temporary.renameTo(file)) {
            file.writeBytes(bytes)
            temporary.delete()
        }
    }

    fun newIdentifier(prefix: String): String =
        prefix + UUID.randomUUID().toString().lowercase()
}
