package com.oshicue.tempo.data

import android.content.ContentValues
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaMetadataRetriever
import android.media.MediaPlayer
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.oshicue.tempo.R
import com.oshicue.tempo.i18n.L10n
import com.oshicue.tempo.i18n.LocalizedMessage
import androidx.core.net.toUri
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File

data class TempoSound(val id: String, val name: String, val subtitle: String)

/**
 * Foreground alerts mix through one MediaPlayer per completion, exactly like the
 * iOS build layers AVAudioPlayers; background alerts belong to the notification
 * channels owned by TempoNotifications.
 */
class TempoAudio(private val context: Context) {
    val builtInSounds: List<TempoSound> = listOf(
        TempoSound("chime", L10n.text("audio.chime.name"), L10n.text("audio.chime.subtitle")),
        TempoSound("pulse", L10n.text("audio.pulse.name"), L10n.text("audio.pulse.subtitle")),
        TempoSound("bell", L10n.text("audio.bell.name"), L10n.text("audio.bell.subtitle")),
        TempoSound("arcade", L10n.text("audio.arcade.name"), L10n.text("audio.arcade.subtitle"))
    )

    var importedSounds by mutableStateOf<List<TempoSound>>(emptyList())
        private set
    var error by mutableStateOf<LocalizedMessage?>(null)

    val sounds: List<TempoSound> get() = builtInSounds + importedSounds

    private data class ImportedRecord(
        val id: String,
        val name: String,
        val fileName: String,
        val duration: Double,
        val mediaUri: String? = null
    )

    private var records: List<ImportedRecord> = emptyList()
    private val expiryPlayers = LinkedHashMap<MediaPlayer, Unit>()
    private var previewPlayer: MediaPlayer? = null
    private var audioFocusRequest: AudioFocusRequest? = null

    private val soundsDirectory: File get() = TempoFiles.soundsDirectory(context)
    private val metadataFile: File get() = File(soundsDirectory, "tempo-imports.json")

    init {
        loadRecords()
    }

    private fun loadRecords() {
        try {
            if (metadataFile.exists()) {
                records = decodeRecords(metadataFile.readText())
                    .filter { File(soundsDirectory, it.fileName).exists() }
            }
            updateImportedSounds()
        } catch (exception: Exception) {
            error = LocalizedMessage.of("audio.importedReadFailed", exception.message ?: metadataFile.name)
        }
    }

    private fun decodeRecords(text: String): List<ImportedRecord> {
        val array = Json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
        return array.mapNotNull { element ->
            val objectValue = element as? JsonObject ?: return@mapNotNull null
            val id = objectValue["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = objectValue["name"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val fileName = objectValue["fileName"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val duration = objectValue["duration"]?.jsonPrimitive?.content?.toDoubleOrNull()
                ?: return@mapNotNull null
            val mediaUri = objectValue["mediaUri"]?.jsonPrimitive?.content
            ImportedRecord(id, name, fileName, duration, mediaUri)
        }
    }

    private fun saveRecords() {
        val array = JsonArray(records.map { record ->
            val map = sortedMapOf<String, kotlinx.serialization.json.JsonElement>(
                "duration" to JsonPrimitive(record.duration),
                "fileName" to JsonPrimitive(record.fileName),
                "id" to JsonPrimitive(record.id),
                "name" to JsonPrimitive(record.name),
                "version" to JsonPrimitive(1)
            )
            record.mediaUri?.let { map["mediaUri"] = JsonPrimitive(it) }
            JsonObject(map)
        })
        TempoFiles.writeAtomic(metadataFile, array.toString().toByteArray(Charsets.UTF_8))
    }

    private fun updateImportedSounds() {
        importedSounds = records.map {
            TempoSound(it.id, it.name, L10n.format("audio.importedSubtitle", it.duration))
        }
    }

    fun title(soundID: String): String =
        sounds.firstOrNull { it.id == soundID }?.name ?: L10n.text("audio.systemSound")

    fun soundFile(soundID: String): File? {
        val record = records.firstOrNull { it.id == soundID } ?: return null
        val file = File(soundsDirectory, record.fileName)
        return if (file.exists()) file else null
    }

    /** Playback URI for foreground alerts and previews. */
    fun soundUri(soundID: String): Uri? {
        if (records.any { it.id == soundID }) {
            return soundFile(soundID)?.let { Uri.fromFile(it) }
        }
        val resourceId = builtInResource(soundID) ?: return null
        return "android.resource://${context.packageName}/$resourceId".toUri()
    }

    /**
     * The URI embedded in a notification channel. Built-in cues resolve through the
     * package resources; imported cues reuse the MediaStore copy so the system UI can
     * read them. Null falls back to the system default sound.
     */
    fun notificationSoundUri(soundID: String): Uri? {
        records.firstOrNull { it.id == soundID }?.let { record ->
            record.mediaUri?.let { return it.toUri() }
            return null
        }
        val resourceId = builtInResource(soundID) ?: return null
        return "android.resource://${context.packageName}/$resourceId".toUri()
    }

    /** Direct resource references so release shrinking cannot drop the built-in cues. */
    private fun builtInResource(soundID: String): Int? = when (soundID) {
        "chime" -> R.raw.chime
        "pulse" -> R.raw.pulse
        "bell" -> R.raw.bell
        "arcade" -> R.raw.arcade
        else -> null
    }

    /** Every timer sounds immediately, including when another member's cue is still playing. */
    fun play(soundID: String) {
        stopPreview()
        val uri = soundUri(soundID) ?: soundUri("chime")
        if (uri == null) {
            error = LocalizedMessage.of("audio.fileNotFound")
            return
        }
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.setOnPreparedListener { prepared ->
                expiryPlayers[prepared] = Unit
                normalizeExpiryVolumes()
                requestFocus()
                prepared.start()
            }
            player.setOnCompletionListener { finished -> finish(finished, interrupted = false) }
            player.setOnErrorListener { failed, _, _ ->
                finish(failed, interrupted = true, reportProblem = true)
                true
            }
            player.prepareAsync()
        } catch (exception: Exception) {
            deactivateIfIdle()
            error = LocalizedMessage.of("audio.playbackError", exception.message ?: "MediaPlayer")
        }
    }

    fun preview(soundID: String) {
        stopPreview()
        // Timer completions take priority over a settings preview.
        if (expiryPlayers.isNotEmpty()) return
        val uri = soundUri(soundID) ?: return
        try {
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build()
            )
            player.setDataSource(context, uri)
            player.setOnPreparedListener { prepared ->
                previewPlayer = prepared
                requestFocus()
                prepared.start()
            }
            player.setOnCompletionListener { finished ->
                if (previewPlayer === finished) {
                    previewPlayer = null
                    finished.release()
                    deactivateIfIdle()
                }
            }
            player.setOnErrorListener { failed, _, _ ->
                if (previewPlayer === failed) {
                    previewPlayer = null
                    failed.release()
                    error = LocalizedMessage.of("audio.previewInterrupted")
                    deactivateIfIdle()
                }
                true
            }
            player.prepareAsync()
        } catch (exception: Exception) {
            stopPreview()
            error = LocalizedMessage.of("audio.previewError", exception.message ?: "MediaPlayer")
        }
    }

    fun stopPreview() {
        previewPlayer?.let {
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.stop()
            it.release()
        }
        previewPlayer = null
        deactivateIfIdle()
    }

    fun stop() {
        stopPreview()
        expiryPlayers.keys.toList().forEach { player ->
            player.setOnCompletionListener(null)
            player.setOnErrorListener(null)
            player.stop()
            player.release()
        }
        expiryPlayers.clear()
        abandonFocus()
    }

    /**
     * Imports a recording chosen through the system file picker. The file must be
     * shorter than 30 seconds and no larger than 50 MB; it is stored untouched, and
     * a MediaStore copy is prepared so the system can play it for notifications.
     */
    fun importSound(source: Uri): String {
        val resolver = context.contentResolver
        var displayName: String? = null
        var size = -1L
        resolver.query(source, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex >= 0) displayName = cursor.getString(nameIndex)
                val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
            }
        }
        val extension = displayName?.substringAfterLast('.', "")?.lowercase()
        if (!extension.isNullOrEmpty() && extension !in SUPPORTED_EXTENSIONS) {
            throw TempoImportException(TempoImportError.Unsupported)
        }
        if (size == 0L) throw TempoImportException(TempoImportError.Empty)
        if (size > 50_000_000L) throw TempoImportException(TempoImportError.TooLarge)

        val id = TempoFiles.newIdentifier("import-")
        val staged = File(context.cacheDir, "$id.${extension.orEmpty().ifEmpty { "audio" }}")
        try {
            resolver.openInputStream(source)?.use { input ->
                staged.outputStream().use { output -> input.copyTo(output) }
            } ?: throw TempoImportException(TempoImportError.Unreadable)
            if (staged.length() <= 0L) throw TempoImportException(TempoImportError.Empty)
            if (staged.length() > 50_000_000L) throw TempoImportException(TempoImportError.TooLarge)

            val duration = readDurationSeconds(staged)
                ?: throw TempoImportException(TempoImportError.Unreadable)
            if (duration <= 0.0) throw TempoImportException(TempoImportError.Empty)
            if (duration >= 30.0) throw TempoImportException(TempoImportError.TooLong)

            val fileName = "$id.${extension.orEmpty().ifEmpty { "audio" }}"
            val destination = File(soundsDirectory, fileName)
            TempoFiles.writeAtomic(destination, staged.readBytes())

            val mediaUri = try {
                insertNotificationCopy(destination, displayName ?: id)
            } catch (exception: Exception) {
                error = LocalizedMessage.of("audio.notificationPreparationFailed")
                null
            }

            val record = ImportedRecord(
                id = id,
                name = displayName?.substringBeforeLast('.')?.ifEmpty { null } ?: id,
                fileName = fileName,
                duration = duration,
                mediaUri = mediaUri?.toString()
            )
            records = records + record
            saveRecords()
            updateImportedSounds()
            return id
        } catch (exception: Exception) {
            staged.delete()
            throw exception
        } finally {
            staged.delete()
        }
    }

    private fun readDurationSeconds(file: File): Double? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            val hasAudio = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_HAS_AUDIO)
            if (hasAudio != "yes") return null
            val milliseconds = retriever
                .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: return null
            milliseconds / 1000.0
        } catch (_: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (_: Exception) {
                // Retriever already released.
            }
        }
    }

    private fun insertNotificationCopy(file: File, displayName: String): Uri? {
        val mime = mimeTypeFor(file.name) ?: "audio/*"
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, "Oshicue Tempo · $displayName")
            put(MediaStore.Audio.Media.MIME_TYPE, mime)
            put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
            put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_NOTIFICATIONS)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: return null
        try {
            resolver.openOutputStream(uri)?.use { output ->
                file.inputStream().use { input -> input.copyTo(output) }
            } ?: throw IllegalStateException("no output stream")
        } catch (exception: Exception) {
            resolver.delete(uri, null, null)
            throw exception
        }
        return uri
    }

    private fun mimeTypeFor(fileName: String): String? = when (fileName.substringAfterLast('.', "").lowercase()) {
        "m4a", "aac" -> "audio/mp4"
        "wav" -> "audio/x-wav"
        "aif", "aiff" -> "audio/x-aiff"
        "caf" -> "audio/x-caf"
        "mp3" -> "audio/mpeg"
        else -> null
    }

    private fun normalizeExpiryVolumes() {
        if (expiryPlayers.isEmpty()) return
        // Linear gain sharing bounds the combined peak, even for identical imported recordings.
        val gain = 1f / expiryPlayers.size
        expiryPlayers.keys.forEach { it.setVolume(gain, gain) }
    }

    private fun finish(player: MediaPlayer, interrupted: Boolean, reportProblem: Boolean = false) {
        if (previewPlayer === player) {
            previewPlayer = null
            player.release()
            if (reportProblem) error = LocalizedMessage.of("audio.previewInterrupted")
            deactivateIfIdle()
            return
        }
        if (expiryPlayers.remove(player) == null) return
        player.release()
        if (reportProblem) error = LocalizedMessage.of("audio.playbackInterrupted")
        normalizeExpiryVolumes()
        deactivateIfIdle()
    }

    private fun requestFocus() {
        if (audioFocusRequest != null) return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val attributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(attributes)
            .setWillPauseWhenDucked(false)
            .build()
        if (audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            audioFocusRequest = request
        }
    }

    private fun deactivateIfIdle() {
        if (expiryPlayers.isEmpty() && previewPlayer == null) abandonFocus()
    }

    private fun abandonFocus() {
        val request = audioFocusRequest ?: return
        val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.abandonAudioFocusRequest(request)
        audioFocusRequest = null
    }

    private companion object {
        val SUPPORTED_EXTENSIONS = setOf("m4a", "wav", "aif", "aiff", "caf", "mp3", "aac")
    }
}

enum class TempoImportError(val key: String) {
    Unsupported("audio.import.unsupported"),
    Empty("audio.import.empty"),
    TooLong("audio.import.tooLong"),
    TooLarge("audio.import.tooLarge"),
    Unreadable("audio.import.unreadable");
}

class TempoImportException(val error: TempoImportError) : Exception(error.key) {
    val messageKey: String get() = error.key
}

/** Records the version key while keeping the imported list forward compatible. */
internal val JsonObject.importVersion: Int?
    get() = (this["version"] as? JsonPrimitive)?.int
