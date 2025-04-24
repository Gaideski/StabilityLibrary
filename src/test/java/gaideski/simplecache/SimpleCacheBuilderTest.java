package gaideski.simplecache;

import org.gaideski.simplecache.SimpleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;

import static org.junit.jupiter.api.Assertions.*;

class SimpleCacheTest {

    private SimpleCache<String, User> userCache;

    @BeforeEach
    void setUp() {
        userCache = new SimpleCache.Builder<String, User>()
                .setMaxCapacity(100)
                .setExpirationAfterLastAccess(Duration.ofMinutes(10))
                .build();
    }

    @Test
    void basicOperations() {
        // Test put and get
        User user1 = new User("1", "John", "Doe", 30);
        userCache.put("user1", user1);

        assertEquals(user1, userCache.get("user1"));
        assertEquals(1, userCache.getSize());

        // Test invalidate
        assertTrue(userCache.invalidate("user1"));
        assertNull(userCache.get("user1"));
        assertEquals(0, userCache.getSize());

        // Test invalidateAll
        userCache.put("user1", user1);
        userCache.put("user2", new User("2", "Jane", "Smith", 25));
        assertEquals(2, userCache.getSize());

        userCache.invalidateAll();
        assertEquals(0, userCache.getSize());
    }

    @Test
    void capacityLimits() {
        SimpleCache<Integer, String> limitedCache = new SimpleCache.Builder<Integer, String>()
                .setMaxCapacity(3)
                .build();

        limitedCache.put(1, "One");
        limitedCache.put(2, "Two");
        limitedCache.put(3, "Three");
        assertEquals(3, limitedCache.getSize());

        // Adding a fourth entry should evict the least recently accessed
        limitedCache. put(4, "Four");
        assertEquals(3, limitedCache.getSize());
        assertNull(limitedCache.get(1)); // Assuming 1 was the least recently accessed
    }

    @Test
    void expirationAfterCreation() throws InterruptedException {
        SimpleCache<String, String> expiringCache = new SimpleCache.Builder<String, String>()
                .setExpirationAfterCreation(Duration.ofMillis(100))
                .setCleanupFrequency(Duration.ofMillis(50))
                .build();

        expiringCache.put("key", "value");
        assertEquals("value", expiringCache.get("key"));

        // Wait for expiration
        TimeUnit.MILLISECONDS.sleep(200);

        // Entry should be expired and removed
        assertNull(expiringCache.get("key"));
    }

    @Test
    void expirationAfterLastAccess() throws InterruptedException {
        SimpleCache<String, String> expiringCache = new SimpleCache.Builder<String, String>()
                .setExpirationAfterLastAccess(Duration.ofMillis(100))
                .setCleanupFrequency(Duration.ofMillis(50))
                .build();

        expiringCache.put("key", "value");

        // Access the value to reset the last access time
        assertEquals("value", expiringCache.get("key"));

        // Wait for half the expiration time
        TimeUnit.MILLISECONDS.sleep(50);

        // Access again to reset timer
        assertEquals("value", expiringCache.get("key"));

        // Wait for expiration time after last access
        TimeUnit.MILLISECONDS.sleep(150);

        // Entry should be expired and removed
        assertNull(expiringCache.get("key"));
    }

    @Test
    void automaticLoading() {
        SimpleCache<Integer, String> loadingCache = new SimpleCache.Builder<Integer, String>()
                .setRetrievalFunctionWhenCacheMiss(key -> "Generated " + key)
                .build();

        // Cache should auto-load missing values
        assertEquals("Generated 1", loadingCache.get(1));
        assertEquals("Generated 2", loadingCache.get(2));

        // Change value and verify it's updated
        loadingCache.put(1, "Updated 1");
        assertEquals("Updated 1", loadingCache.get(1));
    }

    @Test
    void getOptional() {
        User user = new User("1", "John", "Doe", 30);
        userCache.put("user1", user);

        Optional<User> optionalUser = userCache.getOptional("user1");
        assertTrue(optionalUser.isPresent());
        assertEquals(user, optionalUser.get());

        Optional<User> missingUser = userCache.getOptional("missing");
        assertFalse(missingUser.isPresent());
    }

    @Test
    void computeIfAbsent() {
        assertEquals(0, userCache.getSize());

        User user = userCache.computeIfAbsent("user1", key -> new User("1", "John", "Doe", 30));
        assertNotNull(user);
        assertEquals("John", user.getFirstName());
        assertEquals(1, userCache.getSize());

        // Should return cached value without recomputing
        User sameUser = userCache.computeIfAbsent("user1", key -> new User("1", "Different", "Name", 99));
        assertEquals("John", sameUser.getFirstName()); // Should still be the original value
    }

    // Secondary Index Tests

    @Test
    void createSecondaryIndex() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);
        User user3 = new User("3", "Bob", "Johnson", 25);

        userCache.put("user1", user1);
        userCache.put("user2", user2);
        userCache.put("user3", user3);

        // Create an index on the age property
        userCache.createSecondaryIndex("ageIndex", User::getAge);

        assertTrue(userCache.hasIndex("ageIndex"));
        assertEquals(Arrays.asList("ageIndex"), userCache.getIndexNames());
    }

    @Test
    void findByIndex() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);
        User user3 = new User("3", "Bob", "Johnson", 25);

        userCache.put("user1", user1);
        userCache.put("user2", user2);
        userCache.put("user3", user3);

        // Create an index on the age property
        userCache.createSecondaryIndex("ageIndex", User::getAge);

        // Find all users with age 30
        List<User> users = userCache.findByIndex("ageIndex", 30);
        assertEquals(2, users.size());
        assertTrue(users.contains(user1));
        assertTrue(users.contains(user2));

        // Find all users with age 25
        List<User> youngUsers = userCache.findByIndex("ageIndex", 25);
        assertEquals(1, youngUsers.size());
        assertTrue(youngUsers.contains(user3));

        // Find all users with non-existent age
        List<User> noUsers = userCache.findByIndex("ageIndex", 50);
        assertTrue(noUsers.isEmpty());
    }

    @Test
    void multipleSecondaryIndexes() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);
        User user3 = new User("3", "John", "Johnson", 25);

        userCache.put("user1", user1);
        userCache.put("user2", user2);
        userCache.put("user3", user3);

        // Create multiple indexes
        userCache.createSecondaryIndex("ageIndex", User::getAge);
        userCache.createSecondaryIndex("firstNameIndex", User::getFirstName);

        assertEquals(2, userCache.getIndexNames().size());
        assertTrue(userCache.hasIndex("ageIndex"));
        assertTrue(userCache.hasIndex("firstNameIndex"));

        // Find by firstName
        List<User> johnsUsers = userCache.findByIndex("firstNameIndex", "John");
        assertEquals(2, johnsUsers.size());

        // Find by age
        List<User> thirtyUsers = userCache.findByIndex("ageIndex", 30);
        assertEquals(2, thirtyUsers.size());
    }

    @Test
    void indexUpdatesOnPut() {
        User user1 = new User("1", "John", "Doe", 30);
        userCache.put("user1", user1);

        userCache.createSecondaryIndex("ageIndex", User::getAge);

        // Find by age
        List<User> users = userCache.findByIndex("ageIndex", 30);
        assertEquals(1, users.size());

        // Update user age
        User updatedUser = new User("1", "John", "Doe", 31);
        userCache.put("user1", updatedUser);

        // Original age query should return empty
        users = userCache.findByIndex("ageIndex", 30);
        assertTrue(users.isEmpty());

        // New age query should find the updated user
        users = userCache.findByIndex("ageIndex", 31);
        assertEquals(1, users.size());
        assertEquals(updatedUser, users.get(0));
    }

    @Test
    void indexUpdatesOnInvalidate() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);

        userCache.put("user1", user1);
        userCache.put("user2", user2);

        userCache.createSecondaryIndex("ageIndex", User::getAge);

        // Find by age
        List<User> users = userCache.findByIndex("ageIndex", 30);
        assertEquals(2, users.size());

        // Remove one user
        userCache.invalidate("user1");

        // Query should return only the remaining user
        users = userCache.findByIndex("ageIndex", 30);
        assertEquals(1, users.size());
        assertEquals(user2, users.get(0));
    }

    @Test
    void indexUpdatesOnInvalidateAll() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);

        userCache.put("user1", user1);
        userCache.put("user2", user2);

        userCache.createSecondaryIndex("ageIndex", User::getAge);

        // Find by age
        List<User> users = userCache.findByIndex("ageIndex", 30);
        assertEquals(2, users.size());

        // Clear all
        userCache.invalidateAll();

        // Query should return empty
        users = userCache.findByIndex("ageIndex", 30);
        assertTrue(users.isEmpty());
    }

    @Test
    void createSecondaryIndexInBuilder() {
        SimpleCache<String, User> indexedCache = new SimpleCache.Builder<String, User>()
                .setMaxCapacity(100)
                .addSecondaryIndex("ageIndex", User::getAge)
                .addSecondaryIndex("firstNameIndex", User::getFirstName)
                .build();

        assertTrue(indexedCache.hasIndex("ageIndex"));
        assertTrue(indexedCache.hasIndex("firstNameIndex"));

        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 30);

        indexedCache.put("user1", user1);
        indexedCache.put("user2", user2);

        // Find by firstName
        List<User> janeUsers = indexedCache.findByIndex("firstNameIndex", "Jane");
        assertEquals(1, janeUsers.size());
        assertEquals(user2, janeUsers.get(0));
    }

    @Test
    void findByPredicate() {
        User user1 = new User("1", "John", "Doe", 30);
        User user2 = new User("2", "Jane", "Smith", 25);
        User user3 = new User("3", "Bob", "Johnson", 35);

        userCache.put("user1", user1);
        userCache.put("user2", user2);
        userCache.put("user3", user3);

        // Find users older than 27
        Predicate<User> ageOver27 = user -> user.getAge() > 27;
        List<User> olderUsers = userCache.findByPredicate(ageOver27);

        assertEquals(2, olderUsers.size());
        assertTrue(olderUsers.contains(user1));
        assertTrue(olderUsers.contains(user3));

        // Find users with first name containing 'J'
        Predicate<User> nameWithJ = user -> user.getFirstName().contains("J");
        List<User> jUsers = userCache.findByPredicate(nameWithJ);

        assertEquals(2, jUsers.size());
        assertTrue(jUsers.contains(user1));
        assertTrue(jUsers.contains(user2));
    }

    @Test
    void invalidSecondaryIndexOperations() {
        User user = new User("1", "John", "Doe", 30);
        userCache.put("user1", user);

        // Create a valid index
        userCache.createSecondaryIndex("ageIndex", User::getAge);

        // Try to create duplicate index
        Exception exception = assertThrows(IllegalArgumentException.class, () -> {
            userCache.createSecondaryIndex("ageIndex", User::getAge);
        });
        assertTrue(exception.getMessage().contains("already exists"));

        // Try to query non-existent index
        exception = assertThrows(IllegalArgumentException.class, () -> {
            userCache.findByIndex("nonExistentIndex", "value");
        });
        assertTrue(exception.getMessage().contains("not found"));
    }

    // User test class
    static class User {
        private final String id;
        private final String firstName;
        private final String lastName;
        private final int age;

        public User(String id, String firstName, String lastName, int age) {
            this.id = id;
            this.firstName = firstName;
            this.lastName = lastName;
            this.age = age;
        }

        public String getId() {
            return id;
        }

        public String getFirstName() {
            return firstName;
        }

        public String getLastName() {
            return lastName;
        }

        public int getAge() {
            return age;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            User user = (User) o;

            if (age != user.age) return false;
            if (!id.equals(user.id)) return false;
            if (!firstName.equals(user.firstName)) return false;
            return lastName.equals(user.lastName);
        }

        @Override
        public int hashCode() {
            int result = id.hashCode();
            result = 31 * result + firstName.hashCode();
            result = 31 * result + lastName.hashCode();
            result = 31 * result + age;
            return result;
        }
    }
}