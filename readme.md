# SimpleCache

## Overview

SimpleCache is a flexible, lightweight, and thread-safe in-memory caching solution for Java applications. It provides advanced caching capabilities with support for:
- Maximum capacity management
- Time-based expiration
- Automatic value loading
- Concurrent access
- Secondary indexing for efficient lookups

## Features

- 🚀 Thread-safe cache implementation
- 📦 Configurable maximum capacity
- ⏰ Expiration strategies:
  - Expire after creation time
  - Expire after last access
- 🔄 Automatic value loading for cache misses
- 🔍 Secondary indexes for property-based lookups
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
        .addSecondaryIndex("emailIndex", User::getEmail) // Create an index on email property
        .build();

// Find a user by email using the secondary index
List<User> usersWithEmail = userCache.findByIndex("emailIndex", "john@example.com");
```

### Secondary Indexing

SimpleCache now supports secondary indexes for efficient lookups by value properties:

```java
// Create a session cache with a secondary index on userId
SimpleCache<String, UserSession> sessionCache = 
    new SimpleCache
        .Builder<String, UserSession>()
        .setCleanupFrequency(Duration.ofSeconds(10))
        .setMaxCapacity(config.maxSessions())
        .setExpirationAfterLastAccess(config.getSessionTimeToLive())
        .addSecondaryIndex("USER_INDEX", UserSession::getUserId)
        .build();

// Find all sessions for a specific user
List<UserSession> userSessions = sessionCache.findByIndex("USER_INDEX", "user123");
```

You can also add secondary indexes after cache creation:

```java
// Create a secondary index on user age
userCache.createSecondaryIndex("ageIndex", User::getAge);

// Find all users with age 30
List<User> thirtyYearOlds = userCache.findByIndex("ageIndex", 30);
```

### Secondary Index Methods

- `addSecondaryIndex(String indexName, Function<V, Object> propertyExtractor)`:
  - Add a secondary index during cache construction
  - Specify a name for the index and a function to extract the indexed property

- `createSecondaryIndex(String indexName, Function<V, Object> propertyExtractor)`:
  - Create a secondary index on an existing cache
  - All existing entries will be indexed

- `findByIndex(String indexName, Object propertyValue)`:
  - Find all values matching the specified property value
  - Returns a list of values

- `findByPredicate(Predicate<V> predicate)`:
  - Find all values matching a custom predicate
  - Useful for complex search criteria

- `hasIndex(String indexName)`:
  - Check if a secondary index exists

- `getIndexNames()`:
  - Get a list of all secondary index names

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
- Secondary indexes for efficient property-based lookups

## Example: User Cache with Secondary Index
```java
public class UserService {
    private SimpleCache<String, User> userCache;
    
    public UserService() {
        userCache = SimpleCache.Builder.<String, User>newBuilder()
            .setMaxCapacity(1000)
            .setExpirationAfterLastAccess(Duration.ofMinutes(30))
            .setRetrievalFunctionWhenCacheMiss(this::fetchUserFromDatabase)
            .addSecondaryIndex("emailIndex", User::getEmail)
            .addSecondaryIndex("ageIndex", User::getAge)
            .build();
    }
    
    private User fetchUserFromDatabase(String userId) {
        // Implement database lookup
        return database.findUserById(userId);
    }
    
    public User getUser(String userId) {
        return userCache.get(userId);
    }
    
    public List<User> getUsersByEmail(String email) {
        return userCache.findByIndex("emailIndex", email);
    }
    
    public List<User> getUsersByAge(int age) {
        return userCache.findByIndex("ageIndex", age);
    }
    
    public List<User> getUsersOlderThan(int age) {
        return userCache.findByPredicate(user -> user.getAge() > age);
    }
}
```

## Session Management Example
```java
public class SessionManager {
    private static final String USER_INDEX = "userIndex";
    private SimpleCache<String, UserSession> sessionCache;
    
    public SessionManager(SessionConfig config) {
        sessionCache = new SimpleCache
            .Builder<String, UserSession>()
            .setCleanupFrequency(Duration.ofSeconds(10))
            .setMaxCapacity(config.maxSessions())
            .setExpirationAfterLastAccess(config.getSessionTimeToLive())
            .addSecondaryIndex(USER_INDEX, UserSession::getUserId)
            .build();
    }
    
    public UserSession getSession(String sessionId) {
        return sessionCache.get(sessionId);
    }
    
    public List<UserSession> getSessionsForUser(String userId) {
        return sessionCache.findByIndex(USER_INDEX, userId);
    }
    
    public void invalidateUserSessions(String userId) {
        List<UserSession> userSessions = sessionCache.findByIndex(USER_INDEX, userId);
        userSessions.forEach(session -> sessionCache.invalidate(session.getSessionId()));
    }
}
```

## Thread Safety

- Safe for concurrent read and write operations
- Uses `ConcurrentHashMap` internally
- Supports multi-threaded environments
- Secondary indexes are maintained atomically with cache updates

## Limitations

- In-memory cache (not distributed)
- Entire cache content stored in memory
- Not suitable for very large datasets
- Secondary indexes increase memory usage

## Contributing

Contributions are welcome! Please submit pull requests or open issues on the project repository.