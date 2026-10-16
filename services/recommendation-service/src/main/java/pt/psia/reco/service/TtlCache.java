package pt.psia.reco.service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny thread-safe LRU cache with a time-to-live. Enough for "same question again" without a new dependency. */
final class TtlCache<K, V> {

    /** Represents an entry in the cache, holding the value and its expiration time in nanoseconds. */
    private record Entry<V>(V value, long expiresAtNanos) {
    }

    /** The underlying map that stores the cache entries, with a maximum size and LRU eviction policy. */
    private final Map<K, Entry<V>> map;

    /** The time-to-live duration for each entry in the cache, in nanoseconds. */
    private final long ttlNanos;

    /** Creates a new TtlCache with the specified maximum number of entries and time-to-live.
     * @param maxEntries the maximum number of entries to keep in the cache
     * @param ttl the time-to-live duration for each entry in the cache
     */
    TtlCache(int maxEntries, Duration ttl) {
        this.ttlNanos = ttl.toNanos();
        this.map = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > maxEntries;
            }
        });
    }

    /** Gets the value associated with the specified key from the cache, or null if the key is not present or has expired. */
    V get(K key) {
        Entry<V> entry = map.get(key);
        if (entry == null) {
            return null;
        }
        if (System.nanoTime() - entry.expiresAtNanos() >= 0) {
            map.remove(key);
            return null;
        }
        return entry.value();
    }

    /** Put a key-value pair in the cache. */
    void put(K key, V value) {
        map.put(key, new Entry<>(value, System.nanoTime() + ttlNanos));
    }
}
