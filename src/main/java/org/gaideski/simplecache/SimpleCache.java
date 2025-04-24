package org.gaideski.simplecache;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class SimpleCache<K, V> {
    private final Map<K, CachedEntity<V>> cache = new ConcurrentHashMap<>();
    private final long maxCapacity;
    private final Duration expireAfterCreation;
    private final Duration expireAfterLastAccess;
    private final Function<K, V> loadingFunction;
    private final ScheduledExecutorService cleanupExecutor;
    private final ExecutorService service;

    // Store property extractors with index name
    private final Map<String, Function<V, Object>> propertyExtractors = new ConcurrentHashMap<>();
    // Secondary index support - maps property values to cache keys
    private final Map<String, Map<Object, Set<K>>> secondaryIndexes = new ConcurrentHashMap<>();

    private SimpleCache(long maxCapacity, Duration expireAfterCreation, Duration expireAfterLastAccess,
                        Function<K, V> retrieveIfCacheMiss, Duration cleanupTaskFrequency) {
        this.maxCapacity = maxCapacity;
        this.expireAfterCreation = expireAfterCreation;
        this.expireAfterLastAccess = expireAfterLastAccess;
        this.loadingFunction = retrieveIfCacheMiss;

        Duration cleanupFrequency = cleanupTaskFrequency == null ? Duration.ofMinutes(1) : cleanupTaskFrequency;

        if (expireAfterCreation != null || expireAfterLastAccess != null) {
            cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
            service = Executors.newSingleThreadExecutor();
            schedule(this::cleanupExpiredEntries, cleanupFrequency.toSeconds());
        } else {
            cleanupExecutor = null;
            service = null;
        }
    }

    private void schedule(Runnable command, long delay) {
        cleanupExecutor.schedule(() -> service.execute(command), delay, TimeUnit.SECONDS);
    }

    /**
     * Cache new entry
     *
     * @param key   key to be added
     * @param value value to be added
     * @return the previous value associated with key, or null if there was no mapping for see {@link Map#put(Object, Object)}
     */
    public CachedEntity<V> put(K key, V value) {
        ensureCapacity();
        CachedEntity<V> previousEntity = cache.put(key, new CachedEntity<>(value));

        // Update secondary indexes if defined
        for (String indexName : secondaryIndexes.keySet()) {
            updateSecondaryIndex(indexName, key, value);
        }

        return previousEntity;
    }

    /**
     * Create a secondary index based on a property extractor
     * @param indexName unique name for the index
     * @param propertyExtractor function to extract the indexable property from values
     */
    public synchronized void createSecondaryIndex(String indexName, Function<V, Object> propertyExtractor) {
        if (secondaryIndexes.containsKey(indexName)) {
            throw new IllegalArgumentException("Secondary index '" + indexName + "' already exists");
        }

        // Store the property extractor
        propertyExtractors.put(indexName, propertyExtractor);

        // Create the index structure
        Map<Object, Set<K>> index = new ConcurrentHashMap<>();
        secondaryIndexes.put(indexName, index);

        // Index all existing entries
        for (Map.Entry<K, CachedEntity<V>> entry : cache.entrySet()) {
            K key = entry.getKey();
            V value = entry.getValue().getValue();
            Object indexValue = propertyExtractor.apply(value);
            if (indexValue != null) {
                index.computeIfAbsent(indexValue, k -> ConcurrentHashMap.newKeySet()).add(key);
            }
        }
    }

    /**
     * Find entries by a specified property value using a secondary index
     * @param indexName the name of the secondary index
     * @param propertyValue the value to search for
     * @return list of values matching the criteria
     */
    public List<V> findByIndex(String indexName, Object propertyValue) {
        Map<Object, Set<K>> index = secondaryIndexes.get(indexName);
        if (index == null) {
            throw new IllegalArgumentException("Secondary index '" + indexName + "' not found");
        }

        Set<K> keys = index.getOrDefault(propertyValue, ConcurrentHashMap.newKeySet());
        return keys.stream()
                .map(this::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());
    }

    /**
     * Check if a secondary index exists with the given name
     * @param indexName the name of the secondary index
     * @return true if the index exists, false otherwise
     */
    public boolean hasIndex(String indexName) {
        return secondaryIndexes.containsKey(indexName);
    }

    /**
     * Get a list of all secondary index names
     * @return list of index names
     */
    public List<String> getIndexNames() {
        return new java.util.ArrayList<>(secondaryIndexes.keySet());
    }

    /**
     * Update the secondary index when a value is added or updated
     */
    private void updateSecondaryIndex(String indexName, K key, V value) {
        // Get the property extractor for this index
        Function<V, Object> propertyExtractor = propertyExtractors.get(indexName);
        if (propertyExtractor == null) {
            return; // This should not happen if indexes are properly maintained
        }

        // Get the secondary index
        Map<Object, Set<K>> index = secondaryIndexes.get(indexName);
        if (index == null) {
            return; // This should not happen if indexes are properly maintained
        }

        // Remove existing entries for this key from the index
        removeFromSecondaryIndex(key, indexName);

        // Add the new entry to the index
        if (value != null) {
            Object indexValue = propertyExtractor.apply(value);
            if (indexValue != null) {
                index.computeIfAbsent(indexValue, k -> ConcurrentHashMap.newKeySet()).add(key);
            }
        }
    }

    /**
     * Remove a key from all secondary indexes
     */
    private void removeFromSecondaryIndexes(K key) {
        for (String indexName : secondaryIndexes.keySet()) {
            removeFromSecondaryIndex(key, indexName);
        }
    }

    /**
     * Remove a key from a specific secondary index
     */
    private void removeFromSecondaryIndex(K key, String indexName) {
        Map<Object, Set<K>> index = secondaryIndexes.get(indexName);
        if (index != null) {
            for (Set<K> keys : index.values()) {
                keys.remove(key);
            }
        }
    }

    /**
     * Find entries matching a predicate
     * @param predicate function to test each value
     * @return list of values matching the predicate
     */
    public List<V> findByPredicate(Predicate<V> predicate) {
        return cache.values().stream()
                .map(CachedEntity::getValue)
                .filter(predicate)
                .collect(Collectors.toList());
    }

    public long getSize() {
        return cache.size();
    }

    public long getMaxCapacity() {
        return this.maxCapacity;
    }

    public long getAvailableCapacity() {
        return this.maxCapacity - cache.size();
    }

    /**
     * Removes all entries within the cache
     */
    public synchronized void invalidateAll() {
        cache.clear();
        // Clear all secondary indexes
        for (Map<Object, Set<K>> index : secondaryIndexes.values()) {
            index.clear();
        }
    }

    /**
     * @param key key to be removed
     * @return true if it´s removed, false if is no longer present
     */
    public boolean invalidate(K key) {
        CachedEntity<V> removed = cache.remove(key);
        if (removed != null) {
            // Remove from secondary indexes
            removeFromSecondaryIndexes(key);
            return true;
        }
        return false;
    }

    public Optional<V> getOptional(K key) {
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns a value from the cache. If the value is not present and a loading function is supplied
     * {@link Builder#setRetrievalFunctionWhenCacheMiss(Function)}
     * the cache will then try to load the value from the function
     *
     * @param key key
     * @return cached value
     */
    public V get(K key) {
        if(key==null){
            return null;
        }
        removeIfExpired(key);
        CachedEntity<V> entry = cache.get(key);

        if (entry != null) {
            entry.updateLastAccessedTime();
            return entry.getValue();
        } else if (loadingFunction != null) {
            return getOrCompute(key);
        }

        return null;
    }

    /**
     * Return the function used to load values to cache.
     * Can be used associated with {@link SimpleCache#computeIfAbsent(Object, Function)}
     *
     * @return the loading function
     */
    public Function<K, V> getLoadingFunction() {
        return this.loadingFunction;
    }

    /**
     * Return the value of the given key or compute the value storing it in the cache.
     * similar to {@link Map#computeIfAbsent(Object, Function)}
     *
     * @param key             key
     * @param mappingFunction function to perform calculation and cache hydration
     * @return value associated with the key
     */
    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction) {
        // Remove key if expired
        removeIfExpired(key);

        //Grab value if it´s present
        CachedEntity<V> entry = cache.get(key);
        if (entry != null) {
            entry.updateLastAccessedTime();
            return entry.getValue();
        }

        synchronized (this) {
            // Check if the value was added from another thread
            entry = cache.get(key);
            if (entry != null) {
                entry.updateLastAccessedTime();
                return entry.getValue();
            }

            V value = mappingFunction.apply(key);
            put(key, value);
            return value;
        }
    }

    private synchronized V getOrCompute(K key) {
        // Verify if other thread updated the cache with the value;

        CachedEntity<V> entry = cache.get(key);
        if (entry != null) {
            entry.updateLastAccessedTime();
            return entry.getValue();
        }

        V value = loadingFunction.apply(key);
        put(key, value);
        return value;
    }

    private void removeIfExpired(K key) {
        CachedEntity<V> entry = cache.get(key);
        if (entry != null && isExpired(entry)) {
            invalidate(key);
        }
    }

    private boolean isExpired(CachedEntity<V> entry) {
        Instant now = Instant.now();
        if (expireAfterCreation != null) {
            if (now.isAfter(entry.createdAt.plus(expireAfterCreation))) {
                return true;
            }
        }
        if (expireAfterLastAccess != null) {
            return now.isAfter(entry.lastAccessedAt.plus(expireAfterLastAccess));
        }
        return false;
    }

    /**
     * If the cache is full, ensure capacity by removing the oldest item
     * see {@link Builder#setMaxCapacity(long)}}
     */
    private synchronized void ensureCapacity() {
        if (cache.size() >= maxCapacity) {
            cache.entrySet().parallelStream()
                    .min(Comparator.comparing(e -> e.getValue().lastAccessedAt))
                    .map(Map.Entry::getKey)
                    .ifPresent(this::invalidate);
        }
    }

    private void cleanupExpiredEntries() {
        List<K> expiredKeys = cache.entrySet().stream()
                .filter(entry -> isExpired(entry.getValue()))
                .map(Map.Entry::getKey)
                .toList();

        // Remove expired entries and update indexes
        for (K key : expiredKeys) {
            invalidate(key);
        }
    }

    public static class Builder<K, V> {
        private long maxCapacity = Long.MAX_VALUE;
        private Duration expireAfterCreation = null;
        private Duration expireAfterLastAccess = null;
        private Duration cleanupFrequency;
        private Function<K, V> retrieveIfCacheMiss = null;
        private final Map<String, Function<V, Object>> indexDefinitions = new HashMap<>();

        /**
         * Set how large the cache can be. Default is Long.MAX_VALUE(2<sup>63</sup>-1)
         *
         * @param maxCapacity max capacity of cache
         * @return SimpleCacheBuilder
         */
        public Builder<K, V> setMaxCapacity(long maxCapacity) {
            if (maxCapacity < 1) {
                throw new IllegalArgumentException("Cache size must be greater than 0");
            }
            this.maxCapacity = maxCapacity;
            return this;
        }

        public Builder<K, V> setExpirationAfterCreation(Duration expirationDate) {
            this.expireAfterCreation = expirationDate;
            return this;
        }

        public Builder<K, V> setCleanupFrequency(Duration frequency) {
            this.cleanupFrequency = frequency;
            return this;
        }

        public Builder<K, V> setExpirationAfterLastAccess(Duration expirationDate) {
            this.expireAfterLastAccess = expirationDate;
            return this;
        }

        public Builder<K, V> setRetrievalFunctionWhenCacheMiss(Function<K, V> retrieveIfCacheMiss) {
            this.retrieveIfCacheMiss = retrieveIfCacheMiss;
            return this;
        }

        /**
         * Add a secondary index definition that will be created when the cache is built
         *
         * @param indexName The unique name for this index
         * @param propertyExtractor Function that extracts the indexable property from values
         * @return SimpleCacheBuilder
         */
        public Builder<K, V> addSecondaryIndex(String indexName, Function<V, Object> propertyExtractor) {
            if (indexName == null || indexName.trim().isEmpty()) {
                throw new IllegalArgumentException("Index name cannot be null or empty");
            }
            if (propertyExtractor == null) {
                throw new IllegalArgumentException("Property extractor cannot be null");
            }
            if (indexDefinitions.containsKey(indexName)) {
                throw new IllegalArgumentException("Index with name '" + indexName + "' already defined");
            }
            indexDefinitions.put(indexName, propertyExtractor);
            return this;
        }

        public SimpleCache<K, V> build() {
            SimpleCache<K, V> cache = new SimpleCache<>(maxCapacity, expireAfterCreation, expireAfterLastAccess, retrieveIfCacheMiss, cleanupFrequency);

            // Create any secondary indexes defined in the builder
            for (Map.Entry<String, Function<V, Object>> indexDef : indexDefinitions.entrySet()) {
                cache.createSecondaryIndex(indexDef.getKey(), indexDef.getValue());
            }

            return cache;
        }
    }

    public static class CachedEntity<V> {
        Instant createdAt;
        Instant lastAccessedAt;
        V value;

        public CachedEntity(V value) {
            this.createdAt = Instant.now();
            this.lastAccessedAt = Instant.now();
            this.value = value;
        }

        public void updateLastAccessedTime() {
            lastAccessedAt = Instant.now();
        }

        public V getValue() {
            return value;
        }
    }
}