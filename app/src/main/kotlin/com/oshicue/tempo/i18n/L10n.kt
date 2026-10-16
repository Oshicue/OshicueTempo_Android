package com.oshicue.tempo.i18n

import android.annotation.SuppressLint
import android.content.Context

/**
 * Resolves the keys generated from the iOS Localizable.strings files against the
 * Android string resources. The activity keeps the application context attached so
 * both UI and background services can look up the current configuration's wording.
 */
@SuppressLint("StaticFieldLeak")
object L10n {
    private var context: Context? = null

    fun attach(context: Context) {
        this.context = context.applicationContext
    }

    fun text(key: String): String {
        val holding = context ?: return key
        val id = KEY_RESOURCES[key] ?: return key
        return holding.getString(id)
    }

    fun format(key: String, vararg arguments: Any): String {
        val holding = context ?: return key
        val id = KEY_RESOURCES[key] ?: return key
        return holding.getString(id, *arguments)
    }

    fun has(key: String): Boolean = KEY_RESOURCES.containsKey(key)
}

/** A resource key plus its format arguments, resolved when shown. */
data class LocalizedMessage(val key: String, val arguments: List<Any> = emptyList()) {
    fun text(): String = L10n.format(key, *arguments.toTypedArray())

    companion object {
        fun of(key: String, vararg arguments: Any): LocalizedMessage =
            LocalizedMessage(key, arguments.toList())
    }
}
