package pt.psia.reco.service;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Tiny thread-safe LRU cache with a time-to-live. Enough for "same question again" without a new dependency. */
final class TtlCache<K, V> {

    private record Entry<V>(V value, long expiresAtNanos) {
    }

    private final Map<K, Entry<V>> map;
    private final long ttlNanos;

    TtlCache(int maxEntries, Duration ttl) {
        this.ttlNanos = ttl.toNanos();
        this.map = Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<K, Entry<V>> eldest) {
                return size() > maxEntries;
            }
        });
    }

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

    void put(K key, V value) {
        map.put(key, new Entry<>(value, System.nanoTime() + ttlNanos));
    }
}
