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

## Installation

### Maven Dependency
```xml
<dependency>
    <groupId>org.gaideski</groupId>
    <artifactId>simple-cache</artifactId>
    <version>1.0.0</version>
</dependency>
```

## Basic Usage

### Creating a Simple Cache
```java
// Basic cache with default settings
SimpleCacheBuilder<String, Integer> builder = new SimpleCacheBuilder<>();
SimpleCacheBuilder.SimpleCache<String, Integer> cache = builder.build();

// Add entries
cache.put("key1", 42);
Integer value = cache.get("key1"); // Returns 42
```


## Advanced Configuration
```java
// Create a cache with advanced configuration
SimpleCacheBuilder.SimpleCache<String, User> userCache = 
    new SimpleCacheBuilder<String, User>()
        .setMaxCapacity(1000)                           // Limit cache size
        .setExpirationAfterCreation(Duration.ofHours(1)) // Expire after 1 hour
        .setExpirationAfterLastAccess(Duration.ofMinutes(30)) // Or 30 minutes after last access
        .setCleanupFrequency(Duration.ofMinutes(5))     // Run cleanup every 5 minutes
        .setRetrievalFunctionIfCacheMiss(this::loadUserFromDatabase) // Auto-load missing entries
        .build();
```

### Cleanup Frequency
- `setCleanupFrequency(Duration frequency)`:
    - Set how often the cache should run its cleanup process
    - Removes expired entries at the specified interval
    - Helps manage memory and remove stale entries

### Capacity Management
- `setMaxCapacity(long maxCapacity)`: Set maximum number of entries
- When cache reaches capacity, oldest entries are removed

### Expiration Strategies
- `setExpirationAfterCreation(Duration duration)`:
    - Entries expire after specified time from creation
- `setExpirationAfterLastAccess(Duration duration)`:
    - Entries expire after specified time since last access

### Automatic Value Loading
- `setRetrievalFunctionWhenCacheMiss(Function<K,V> loadingFunction)`:
    - Automatically compute values for missing cache entries
    - Useful for lazy loading from databases or external services

## Key Methods

- `put(K key, V value)`: Add or update an entry
- `get(K key)`: Retrieve an entry
- `getOptional(K key)`: Retrieve an optional entry
- `invalidate(K key)`: Remove a specific entry
- `invalidateAll()`: Clear entire cache

## Performance Considerations

- Optimized for read-heavy scenarios
- Thread-safe with minimal locking
- Concurrent access supported
- Automatic cleanup of expired entries

## Example: User Cache with Database Retrieval
```java
public class UserService {
    private SimpleCacheBuilder.SimpleCache<String, User> userCache;

    public UserService() {
        userCache = new SimpleCacheBuilder<String, User>()
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

## Limitations

- In-memory cache (not distributed)
- Entire cache content stored in memory
- Not suitable for very large datasets

## Contributing

Contributions are welcome! Please submit pull requests or open issues on the project repository.

## License

[Specify your license here]

## Contact

[Your contact information or project repository]
