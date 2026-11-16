package com.example.cssintegration.cssUtils

import androidx.compose.ui.text.font.FontFamily
import java.util.concurrent.ConcurrentHashMap

/**
 * Registry that lets `font-family` refer to your own fonts by name.
 *
 * Register once at startup (e.g. in `Application.onCreate` or before `setContent`):
 *
 * ```kotlin
 * CssFonts.register("Inter", FontFamily(Font(R.font.inter_regular), Font(R.font.inter_bold, FontWeight.Bold)))
 * ```
 *
 * then use it from CSS: `font-family: "Inter", sans-serif;`. Names are case-insensitive.
 */
object CssFonts {
    private val families = ConcurrentHashMap<String, FontFamily>()

    fun register(name: String, family: FontFamily) {
        families[name.trim().lowercase()] = family
    }

    fun unregister(name: String) {
        families.remove(name.trim().lowercase())
    }

    operator fun get(name: String): FontFamily? = families[name.trim().lowercase()]
}
