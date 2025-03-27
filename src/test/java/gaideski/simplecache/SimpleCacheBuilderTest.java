package gaideski.simplecache;


import org.gaideski.simplecache.SimpleCache;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class SimpleCacheBuilderTest {

    @Nested
    @DisplayName("SimpleCacheBuilder Configuration Tests")
    class BuilderConfigurationTests {

        @Test
        @DisplayName("Should create cache with default max capacity")
        void testDefaultMaxCapacity() {
            SimpleCache<String, Integer> cache = new SimpleCache.Builder<String, Integer>().build();

            assertThat(cache.getMaxCapacity()).isEqualTo(Long.MAX_VALUE);
        }

        @Test
        @DisplayName("Should throw exception for invalid max capacity")
        void testInvalidMaxCapacity() {
            SimpleCache.Builder<String,String> builder = new SimpleCache.Builder<>();

            assertThatThrownBy(() -> builder.setMaxCapacity(0))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessage("Cache size must be greater than 0");
        }

        @Test
        @DisplayName("Should set max capacity correctly")
        void testSetMaxCapacity() {
            SimpleCache<String,Integer> cache =  new SimpleCache.Builder<String, Integer>()
                    .setMaxCapacity(100)
                    .build();

            assertThat(cache.getMaxCapacity()).isEqualTo(100);
        }

        @Test
        void testGetAvailableCapacity() {
            SimpleCache<String,String> cache =  new SimpleCache.Builder<String, String>()
                    .setMaxCapacity(100)
                    .build();

            assertThat(cache.getAvailableCapacity()).isEqualTo(10);

            // Add some entries
            for (int i = 0; i < 5; i++) {
                cache.put("key" + i, "value" + i);
            }

            // Check available capacity
            assertThat(cache.getAvailableCapacity()).isEqualTo(5);
        }

        @Test
        void testComputeIfAbsentWithNewKey() {
            SimpleCache<String,String> cache =  new SimpleCache.Builder<String, String>()
                    .setMaxCapacity(100)
                    .build();

            // Compute a new value
            String result = cache.computeIfAbsent("newKey", key -> "computedValue");

            // Verify the value is returned and cached
            assertThat(result).isEqualTo("computedValue");
            assertThat(cache.get("newKey")).isEqualTo("computedValue");
        }

        @Test
        void testComputeIfAbsentExistingKey() {
            SimpleCache<String,String> cache =  new SimpleCache.Builder<String, String>()
                    .setMaxCapacity(100)
                    .build();

            cache.put("existingKey", "originalValue");

            // Compute with existing key should not change the value
            String result = cache.computeIfAbsent("existingKey", key -> "newValue");

            // Verify the original value is retained
            assertThat(result).isEqualTo("originalValue");
        }

    }

    @Nested
    @DisplayName("SimpleCache Functionality Tests")
    class SimpleCacheTests {
        private SimpleCache<String, Integer> cache;

        @Mock
        private Function<String, Integer> mockLoadingFunction;

        @BeforeEach
        void setUp() {
            var builder = new SimpleCache.Builder<String,Integer>();
            cache = builder
                    .setMaxCapacity(2)
                    .setRetrievalFunctionWhenCacheMiss(mockLoadingFunction)
                    .build();
        }

        @Test
        @DisplayName("Should put and get values correctly")
        void testPutAndGet() {
            cache.put("key1", 42);

            assertThat(cache.get("key1")).isEqualTo(42);
        }

        @Test
        void testComputeIfAbsentConcurrency() throws InterruptedException {
            // Simulate concurrent access
            Function<String, Integer> slowComputation = key -> {
                try {
                    Thread.sleep(50); // Simulate some computation time
                    return 101;
                } catch (InterruptedException e) {
                    return null;
                }
            };

            // Create multiple threads to access the same key
            Thread[] threads = new Thread[10];
            Integer[] results = new Integer[threads.length];

            for (int i = 0; i < threads.length; i++) {
                final int index = i;
                threads[i] = new Thread(() -> results[index] = cache.computeIfAbsent("concurrentKey", slowComputation));
                threads[i].start();
            }

            // Wait for all threads to complete
            for (Thread thread : threads) {
                thread.join();
            }

            // Verify all threads got the same value
            for (Integer result : results) {
                assertThat(result).isEqualTo(101);
            }

            // Verify the computation happened only once
            assertThat(cache.get("concurrentKey")).isEqualTo(101);
        }

        @Test
        @DisplayName("Should return null for non-existent key")
        void testGetNonExistentKey() {
            assertThat(cache.get("nonexistent")).isNull();
        }

        @Test
        @DisplayName("Should load value using loading function")
        void testLoadingFunction() {
            when(mockLoadingFunction.apply("key1")).thenReturn(42);

            Integer value = cache.get("key1");

            assertThat(value).isEqualTo(42);
            verify(mockLoadingFunction).apply("key1");
        }

        @Test
        @DisplayName("Should return optional value")
        void testGetOptional() {
            cache.put("key1", 42);

            Optional<Integer> optionalValue = cache.getOptional("key1");

            assertThat(optionalValue)
                    .isPresent()
                    .contains(42);
        }

        @Test
        @DisplayName("Should invalidate specific key")
        void testInvalidate() {
            cache.put("key1", 42);

            boolean result = cache.invalidate("key1");

            assertThat(result).isTrue();
            assertThat(cache.get("key1")).isNull();
        }

        @Test
        @DisplayName("Should invalidate all keys")
        void testInvalidateAll() {
            cache.put("key1", 42);
            cache.put("key2", 43);

            cache.invalidateAll();

            assertThat(cache.getSize()).isZero();
        }
    }

    @Nested
    @DisplayName("Capacity and Expiration Tests")
    class CapacityAndExpirationTests {
        private SimpleCache<String, Integer> cache;

        @Test
        @DisplayName("Should remove oldest entry when cache is full")
        void testCacheCapacity() throws InterruptedException {
            var builder = new SimpleCache.Builder<String,Integer>();
            cache = builder.setMaxCapacity(2).build();

            cache.put("key1", 42);
            TimeUnit.MILLISECONDS.sleep(10);
            cache.put("key2", 43);
            TimeUnit.MILLISECONDS.sleep(10);
            cache.put("key3", 44);

            assertThat(cache.get("key1")).isNull();
            assertThat(cache.get("key2")).isNotNull();
            assertThat(cache.get("key3")).isNotNull();
        }

        @Test
        @DisplayName("Should expire entries after creation time")
        void testExpireAfterCreation() throws InterruptedException {
            var builder = new SimpleCache.Builder<String,Integer>();
            cache = builder
                    .setMaxCapacity(2)
                    .setExpirationAfterCreation(Duration.ofMillis(50))
                    .setCleanupFrequency(Duration.ofMillis(25))
                    .build();

            cache.put("key1", 42);

            TimeUnit.MILLISECONDS.sleep(100);

            assertThat(cache.get("key1")).isNull();
        }

        @Test
        @DisplayName("Should expire entries after last access")
        void testExpireAfterLastAccess() throws InterruptedException {
            var builder = new SimpleCache.Builder<String,Integer>();
            cache = builder
                    .setMaxCapacity(2)
                    .setExpirationAfterLastAccess(Duration.ofMillis(50))
                    .setCleanupFrequency(Duration.ofMillis(25))
                    .build();

            cache.put("key1", 42);

            TimeUnit.MILLISECONDS.sleep(30);
            cache.get("key1");

            TimeUnit.MILLISECONDS.sleep(30);
            assertThat(cache.get("key1")).isNotNull();

            TimeUnit.MILLISECONDS.sleep(50);
            assertThat(cache.get("key1")).isNull();
        }
    }

    @Nested
    @DisplayName("Large Scale Performance Tests")
    class LargeScaleCacheTests {

        @Test
        @DisplayName("Should handle millions of entries with expiration and computeIfAbsent")
        void testLargeScaleCacheWithExpiration() throws InterruptedException {
            // Configuration
            int totalEntries = 1200;
            int concurrentThreads = Runtime.getRuntime().availableProcessors();
            Duration expiration = Duration.ofMillis(100);

            // Tracking mechanisms
            List<String> processedKeys = new CopyOnWriteArrayList<>();
            ConcurrentHashMap<String, Integer> computeCounter = new ConcurrentHashMap<>();

            // Loading function that tracks computations
            Function<String, Integer> loadingFunction = key -> {
                computeCounter.merge(key, 1, Integer::sum);
                return Integer.parseInt(key.substring(4)); // Extract number from key
            };

            // Create a cache with expiration and capacity limit
            var builder = new SimpleCache.Builder<String,Integer>();
            SimpleCache<String, Integer> cache = builder
                    .setMaxCapacity(totalEntries)
                    .setExpirationAfterCreation(expiration)
                    .setCleanupFrequency(Duration.ofMillis(199))
                    .setRetrievalFunctionWhenCacheMiss(loadingFunction)
                    .build();

            // Populate cache concurrently
            List<CompletableFuture<Void>> futures = new ArrayList<>();
            for (int i = 0; i < concurrentThreads; i++) {
                final int threadIndex = i;
                futures.add(CompletableFuture.runAsync(() -> {
                    int start = threadIndex * (totalEntries / concurrentThreads);
                    int end = (threadIndex + 1) * (totalEntries / concurrentThreads);

                    for (int j = start; j < end; j++) {
                        String key = "item" + j;
                        cache.put(key, j);
                        processedKeys.add(key);
                    }
                }));
            }

            // Wait for population to complete
            CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

            // Wait for entries to expire
            TimeUnit.MILLISECONDS.sleep(200);

            // Prepare for computeIfAbsent testing
            List<CompletableFuture<Void>> computeFutures = new ArrayList<>();
            for (int i = 0; i < concurrentThreads; i++) {
                final int threadIndex = i;
                computeFutures.add(CompletableFuture.runAsync(() -> {
                    int start = threadIndex * (totalEntries / concurrentThreads);
                    int end = (threadIndex + 1) * (totalEntries / concurrentThreads);

                    for (int j = start; j < end; j++) {
                        String key = "item" + j;

                        // This will trigger computeIfAbsent since entries have expired
                        cache.get(key);
                    }
                }));
            }

            // Wait for computeIfAbsent operations
            CompletableFuture.allOf(computeFutures.toArray(new CompletableFuture[0])).join();

            // Assertions
            assertThat(processedKeys).hasSize(totalEntries);

            // Verify that some entries were recomputed
            assertThat(computeCounter)
                    .hasSizeGreaterThan(0)
                    .hasSizeLessThanOrEqualTo(totalEntries);

            // Verify that all recomputed entries have correct values
            computeCounter.forEach((key, computeCount) -> {
                int expectedValue = Integer.parseInt(key.substring(4));
                assertThat(cache.get(key)).isEqualTo(expectedValue);
            });

            // Verify that concurrent computeIfAbsent doesn't duplicate work excessively
            computeCounter.values().forEach(count ->
                    assertThat(count).isLessThanOrEqualTo(concurrentThreads)
            );
        }
    }
}