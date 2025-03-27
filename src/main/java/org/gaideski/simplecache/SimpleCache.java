package org.gaideski.simplecache;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.*;
import java.util.function.Function;

public  class SimpleCache<K,V>{
    public static class Builder<K,V> {

        private long maxCapacity = Long.MAX_VALUE;
        private Duration expireAfterCreation = null;
        private Duration expireAfterLastAccess = null;
        private Duration cleanupFrequency;
        private Function<K, V> retrieveIfCacheMiss = null;


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

        public SimpleCache<K, V> build() {
            return new SimpleCache<>(maxCapacity, expireAfterCreation, expireAfterLastAccess, retrieveIfCacheMiss, cleanupFrequency);
        }
    }

    private final Map<K, CachedEntity<V>> cache = new ConcurrentHashMap<>();
    private final long maxCapacity;
    private final Duration expireAfterCreation;
    private final Duration expireAfterLastAccess;
    private final Function<K,V> loadingFunction;
    private final ScheduledExecutorService cleanupExecutor;
    private final ExecutorService service;

    private SimpleCache(long maxCapacity, Duration expireAfterCreation, Duration expireAfterLastAccess, Function<K, V> retrieveIfCacheMiss, Duration cleanupTaskFrequency) {
        this.maxCapacity = maxCapacity;
        this.expireAfterCreation = expireAfterCreation;
        this.expireAfterLastAccess = expireAfterLastAccess;
        this.loadingFunction = retrieveIfCacheMiss;

        Duration cleanupFrequency = cleanupTaskFrequency == null ? Duration.ofMinutes(1) : cleanupTaskFrequency;

        if(expireAfterCreation!=null || expireAfterLastAccess!=null){
            cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
            service = Executors.newVirtualThreadPerTaskExecutor();
            schedule(this::cleanupExpiredEntries, cleanupFrequency.toSeconds());
        }else {
            cleanupExecutor=null;
            service =null;
        }
    }


    private void schedule(Runnable command, long delay){
        cleanupExecutor.schedule( ()-> service.execute(command), delay, TimeUnit.SECONDS);
    }
    /**
     * Cache new entry
     * @param key key to be added
     * @param value value to be added
     */
    public void put(K key, V value){
        ensureCapacity();
        cache.put(key, new CachedEntity<>(value));
    }

    public long getSize(){
        return cache.size();
    }
    public long getMaxCapacity(){
        return this.maxCapacity;
    }

    public long getAvailableCapacity(){
        return this.maxCapacity - cache.size();
    }

    /**
     * Removes all entries within the cache
     */
    public synchronized void invalidateAll(){
        cache.clear();
    }

    /**
     *
     * @param key key to be removed
     * @return true if it´s removed, false if is no longer present
     */
    public boolean invalidate(K key){
        return cache.remove(key)!=null;
    }

    public Optional<V> getOptional(K key){
        return Optional.ofNullable(get(key));
    }

    /**
     * Returns a value from the cache. If the value is not present and a loading function is supplied
     * {@link Builder#setRetrievalFunctionWhenCacheMiss(Function)}
     * the cache will then try to load the value from the function
     * @param key key
     * @return cached value
     */
    public V get(K key) {
        removeIfExpired(key);
        CachedEntity<V> entry = cache.get(key);

        if(entry !=null){
            entry.updateLastAccessedTime();
            return entry.getValue();
        }

        else if(loadingFunction!=null){
            return getOrCompute(key);
        }

        return null;

    }

    public V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction){
        // Remove key if expired
        removeIfExpired(key);

        //Grab value if it´s present
        CachedEntity<V> entry = cache.get(key);
        if(entry!=null){
            entry.updateLastAccessedTime();
            return entry.getValue();
        }

        synchronized (this){
            // Check if the value was added from another thread
            entry = cache.get(key);
            if(entry != null){
                entry.updateLastAccessedTime();
                return entry.getValue();
            }

            V value = mappingFunction.apply(key);
            put(key,value);
            return value;
        }
    }

    private synchronized V getOrCompute(K key) {
        // Verify if other thread updated the cache with the value;

        CachedEntity<V> entry = cache.get(key);
        if(entry!=null){
            entry.updateLastAccessedTime();
            return entry.getValue();
        }

        V value = loadingFunction.apply(key);
        put(key,value);
        return value;
    }

    private void removeIfExpired(K key) {
        CachedEntity<V> entry = cache.get(key);
        if(entry != null && isExpired(entry)){
            cache.remove(key);
        }
    }

    private boolean isExpired(CachedEntity<V> entry) {
        Instant now = Instant.now();
        if(expireAfterCreation!=null){
            if(now.isAfter(entry.createdAt.plus(expireAfterCreation))){
                return true;
            }
        }
        if(expireAfterLastAccess!=null){
            return now.isAfter(entry.lastAccessedAt.plus(expireAfterLastAccess));
        }
        return false;
    }

    /**
     * If the cache is full, ensure capacity by removing the oldest item
     * see {@link Builder#setMaxCapacity(long)}}
     */
    private synchronized void ensureCapacity() {
        if(cache.size()>=maxCapacity){
            cache.entrySet().parallelStream()
                    .min(Comparator.comparing(e -> e.getValue().lastAccessedAt))
                    .map(Map.Entry::getKey)
                    .ifPresent(cache::remove);
        }
    }

    private void cleanupExpiredEntries(){
        cache.entrySet().removeIf(entry-> isExpired(entry.getValue()));
    }

    public static class CachedEntity<V>{
        Instant createdAt;
        Instant lastAccessedAt;
        V value;

        public CachedEntity(V value) {
            this.createdAt = Instant.now();
            this.lastAccessedAt = Instant.now();
            this.value = value;
        }

        public void updateLastAccessedTime(){
            lastAccessedAt = Instant.now();
        }

        public V getValue(){
            return value;
        }

    }
}

