package com.example.wastickers

import java.text.Normalizer
import java.util.Locale

/**
 * Pure-JVM, in-memory search index for the WASTICKERS list.
 *
 * Why an index: the search field must react to every keystroke over thousands
 * of files without touching storage at all. The normalised haystack (derived
 * pack name + original file name) is computed ONCE per list change on a
 * background dispatcher; each keystroke only does substring matching.
 *
 * Normalisation is intentionally conservative and covers both locales used by
 * the app:
 *  - case folding for English (Locale.ROOT, never the device default),
 *  - insignificant whitespace collapsed,
 *  - Arabic harakat/tatweel removed and Alef/Hamza/Ya/Ta-Marbuta variants
 *    folded, plus Arabic-Indic digits mapped to Latin digits,
 *  - punctuation/separators treated as spaces so "pack_part2" matches
 *    "pack part2" and "حزمة_القطط" matches "القطط".
 */
object WastickersSearchNormalizer {

    private val SEPARATORS = Regex("[^\\p{L}\\p{N}]+")
    private val COMBINING_MARKS = Regex("\\p{M}+")
    private val WHITESPACE = Regex("\\s+")
    private val ZERO_WIDTH = setOf('\u200B', '\u200C', '\u200D', '\u2060', '\uFEFF')

    fun normalize(input: String): String {
        if (input.isBlank()) return ""
        var text = input.trim()
        text = Normalizer.normalize(text, Normalizer.Form.NFKC)
        val mapped = StringBuilder(text.length)
        for (ch in text) {
            if (ch in ZERO_WIDTH || ch == '\u0640') continue // tatweel
            mapped.append(foldArabic(ch))
        }
        // Latin accents and any remaining combining marks.
        val decomposed = Normalizer.normalize(mapped.toString(), Normalizer.Form.NFD)
        val stripped = COMBINING_MARKS.replace(decomposed, "")
        val lower = stripped.lowercase(Locale.ROOT)
        return WHITESPACE.replace(SEPARATORS.replace(lower, " "), " ").trim()
    }

    private fun foldArabic(ch: Char): Char = when (ch) {
        '\u0622', '\u0623', '\u0625', '\u0671' -> '\u0627' // Alef variants
        '\u0649' -> '\u064A'                               // Alif maqsura -> Ya
        '\u0629' -> '\u0647'                               // Ta marbuta -> Ha
        in '\u0660'..'\u0669' -> ('0'.code + (ch.code - '\u0660'.code)).toChar()
        in '\u06F0'..'\u06F9' -> ('0'.code + (ch.code - '\u06F0'.code)).toChar()
        else -> ch
    }
}

/**
 * Snapshot index over an immutable list. Instances are cheap to rebuild when
 * the discovery list changes and are never mutated afterwards, so they are
 * safe to share between the debounced query flow and the UI.
 */
class WastickersSearchIndex(files: List<WastickersFile>) {

    private data class Entry(val haystack: String, val file: WastickersFile)

    private val entries: List<Entry> = ArrayList<Entry>(files.size).apply {
        for (file in files) {
            val haystack = WastickersSearchNormalizer.normalize(
                WastickersFileOps.packNameOf(file.displayName) + " " + file.displayName
            )
            add(Entry(haystack, file))
        }
    }

    val size: Int get() = entries.size

    /** All normalised query tokens must match (AND), each as a substring. */
    fun search(query: String): List<WastickersFile> {
        val normalized = WastickersSearchNormalizer.normalize(query)
        if (normalized.isEmpty()) return entries.map { it.file }
        val tokens = normalized.split(' ').filter { it.isNotEmpty() }
        if (tokens.isEmpty()) return entries.map { it.file }
        val result = ArrayList<WastickersFile>()
        for (entry in entries) {
            if (tokens.all { entry.haystack.contains(it) }) result.add(entry.file)
        }
        return result
    }
}
