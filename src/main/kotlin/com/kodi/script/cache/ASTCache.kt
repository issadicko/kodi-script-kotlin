package com.kodi.script.cache

import com.kodi.script.ast.Program
import java.security.MessageDigest
import java.util.*

/** LRU cache for parsed AST programs. Thread-safe implementation using synchronized methods. */
class ASTCache(private val capacity: Int = 1000) {

    private data class CacheEntry(
            val key: String,
            val source: String, // Store source for collision detection
            val program: Program
    )

    private val cache = LinkedHashMap<String, CacheEntry>(capacity, 0.75f, true)

    /** Generate SHA-256 hash for source code. Uses first 16 characters for shorter keys. */
    private fun hash(source: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(source.toByteArray())
        return hashBytes.take(8).joinToString("") { "%02x".format(it) }
    }

    /** Retrieve cached AST program. Returns null if not found or source mismatch (collision). */
    @Synchronized
    fun get(source: String): Program? {
        val key = hash(source)
        val entry = cache[key] ?: return null

        // Collision detection: verify source matches
        if (entry.source != source) {
            return null
        }

        // Move to end (most recently used)
        cache.remove(key)
        cache[key] = entry

        return entry.program
    }

    /** Store AST program in cache. Evicts oldest entry if at capacity. */
    @Synchronized
    fun set(source: String, program: Program) {
        val key = hash(source)

        // Remove existing entry if present
        cache.remove(key)

        // Evict oldest if at capacity
        if (cache.size >= capacity) {
            val oldest = cache.keys.firstOrNull()
            oldest?.let { cache.remove(it) }
        }

        // Add new entry
        cache[key] = CacheEntry(key, source, program)
    }

    /** Clear all cached entries. */
    @Synchronized
    fun clear() {
        cache.clear()
    }

    /** Get number of cached entries. */
    @Synchronized fun size(): Int = cache.size

    companion object {
        /** Global default cache instance. */
        val default = ASTCache(1000)
    }
}
