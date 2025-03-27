# SimpleCache

## Overview

SimpleCache is a flexible, lightweight, and thread-safe in-memory caching solution for Java applications. It provides advanced caching capabilities with support for:
- Maximum capacity management
- Time-based expiration
- Automatic value loading
- Concurrent access

## Features

- 🚀 Thread-safe cache implementation
- 📦 Configurable maximum capacity
- ⏰ Expiration strategies:
  - Expire after creation time
  - Expire after last access
- 🔄 Automatic value loading for cache misses
- 💻 Lightweight and easy to use

## Basic Usage

### Creating a Simple Cache
```java
// Basic cache with default settings
SimpleCache.Builder<String, Integer> builder = SimpleCache.Builder.newBuilder();
SimpleCache<String, Integer> cache = builder.build();

// Add entries
cache.put("key1", 42);
Integer value = cache.get("key1"); // Returns 42
```

## Advanced Configuration
```java
// Create a cache with advanced configuration
SimpleCache<String, User> userCache = 
    SimpleCache.Builder.<String, User>newBuilder()
        .setMaxCapacity(1000)                           // Limit cache size
        .setExpirationAfterCreation(Duration.ofHours(1)) // Expire after 1 hour
        .setExpirationAfterLastAccess(Duration.ofMinutes(30)) // Or 30 minutes after last access
        .setCleanupFrequency(Duration.ofMinutes(5))     // Run cleanup every 5 minutes
        .setRetrievalFunctionWhenCacheMiss(this::loadUserFromDatabase) // Auto-load missing entries
        .build();
```

### Key Configuration Methods

#### Capacity Management
- `setMaxCapacity(long maxCapacity)`:
  - Set maximum number of entries in the cache
  - Throws `IllegalArgumentException` if capacity is less than 1
  - Default is `Long.MAX_VALUE`

#### Expiration Strategies
- `setExpirationAfterCreation(Duration duration)`:
  - Entries expire after specified time from creation
- `setExpirationAfterLastAccess(Duration duration)`:
  - Entries expire after specified time since last access

#### Cleanup and Loading
- `setCleanupFrequency(Duration frequency)`:
  - Set how often the cache should run its cleanup process
  - Default is 1 minute if not specified
  - Removes expired entries at the specified interval
- `setRetrievalFunctionWhenCacheMiss(Function<K,V> loadingFunction)`:
  - Automatically compute values for missing cache entries
  - Useful for lazy loading from databases or external services

## Key Methods

- `put(K key, V value)`: Add or update an entry
- `get(K key)`: Retrieve an entry
- `getOptional(K key)`: Retrieve an optional entry
- `invalidate(K key)`: Remove a specific entry
- `invalidateAll()`: Clear entire cache
- `getSize()`: Get current cache size
- `getMaxCapacity()`: Get maximum cache capacity
- `getAvailableCapacity()`: Get remaining cache capacity

## Performance Considerations

- Optimized for read-heavy scenarios
- Thread-safe with minimal locking
- Concurrent access supported
- Automatic cleanup of expired entries
- Uses virtual threads for cleanup tasks

## Example: User Cache with Database Retrieval
```java
public class UserService {
    private SimpleCache<String, User> userCache;

    public UserService() {
        userCache = SimpleCache.Builder.<String, User>newBuilder()
            .setMaxCapacity(1000)
            .setExpirationAfterLastAccess(Duration.ofMinutes(30))
            .setRetrievalFunctionWhenCacheMiss(this::fetchUserFromDatabase)
            .build();
    }

    private User fetchUserFromDatabase(String userId) {
        // Implement database lookup
        return database.findUserById(userId);
    }

    public User getUser(String userId) {
        return userCache.get(userId);
    }
}
```

## Thread Safety

- Safe for concurrent read and write operations
- Uses `ConcurrentHashMap` internally
- Supports multi-threaded environments
- Utilizes virtual threads for background tasks

## Limitations

- In-memory cache (not distributed)
- Entire cache content stored in memory
- Not suitable for very large datasets

## Contributing

Contributions are welcome! Please submit pull requests or open issues on the project repository.

