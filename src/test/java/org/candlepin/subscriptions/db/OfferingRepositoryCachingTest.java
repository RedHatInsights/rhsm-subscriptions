/*
 * Copyright Red Hat, Inc.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 *
 * Red Hat trademarks are not licensed under GPLv3. No permission is
 * granted to use or replicate Red Hat trademarks that are incorporated
 * in this software or its documentation.
 */
package org.candlepin.subscriptions.db;

import static java.util.Optional.empty;
import static java.util.Optional.ofNullable;
import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import javax.transaction.Transactional;
import org.candlepin.subscriptions.db.model.Offering;
import org.candlepin.subscriptions.db.model.ServiceLevel;
import org.candlepin.subscriptions.db.model.Usage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.opentest4j.AssertionFailedError;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
public class OfferingRepositoryCachingTest {
  public static final String EXISTS_CACHE = "offeringExists";
  public static final String SKU = "MCT123";
  public static final String SKU2 = "MCT345";
  public static final String SKU3 = "MCT456";

  @Autowired OfferingRepository repository;
  @Autowired CacheManager cacheManager;

  @AfterEach
  void tearDown() {
    cacheManager.getCache(EXISTS_CACHE).clear();
  }

  private <T> Optional<T> getCachedOffering(String sku, String cache, Class<T> type) {
    return ofNullable(cacheManager.getCache(cache)).map(c -> c.get(sku, type));
  }

  private Offering createOffering(String s) {
    var offering1 =
        Offering.builder()
            .sku(s)
            .productName("RHEL")
            .serviceLevel(ServiceLevel.STANDARD)
            .usage(Usage.PRODUCTION)
            .build();
    var offering = offering1;
    assertEquals(empty(), getCachedOffering(s, EXISTS_CACHE, Boolean.class));
    repository.save(offering);
    // Test to make sure the cache is still empty after a save.  In the future we might want to
    // annotate the save methods with a @CachePut to pre-populate the cache, but right now we
    // aren't doing that.
    assertEquals(empty(), getCachedOffering(s, EXISTS_CACHE, Boolean.class));
    return offering;
  }

  @Test
  @Transactional
  void testExistsById() {
    createOffering(SKU);
    assertTrue(repository.existsById(SKU));

    var cacheHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
  }

  @Test
  @Transactional
  void testDeleteAllEvictsEverything() {
    Stream.of(SKU, SKU2).forEach(this::createOffering);
    Stream.of(SKU, SKU2).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAll();

    for (String sku : List.of(SKU, SKU2)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteAllInBatchEvictsEverything() {
    Stream.of(SKU, SKU2).forEach(this::createOffering);
    Stream.of(SKU, SKU2).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAllInBatch();

    for (String sku : List.of(SKU, SKU2)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteAllIterableEvictsEverything() {
    var offering1 = createOffering(SKU);
    var offering2 = createOffering(SKU2);
    createOffering(SKU3);

    Stream.of(SKU, SKU2, SKU3).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAll(List.of(offering1, offering2));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteAllByIdEvictsEverything() {
    Stream.of(SKU, SKU2, SKU3).forEach(this::createOffering);
    Stream.of(SKU, SKU2, SKU3).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAllById(List.of(SKU, SKU2));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteAllInBatchIterableEvictsEverything() {
    var offering1 = createOffering(SKU);
    var offering2 = createOffering(SKU2);
    createOffering(SKU3);

    Stream.of(SKU, SKU2, SKU3).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAllInBatch(List.of(offering1, offering2));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteAllByIdInBatchEvictsEverything() {
    Stream.of(SKU, SKU2, SKU3).forEach(this::createOffering);
    Stream.of(SKU, SKU2, SKU3).forEach(x -> assertTrue(repository.existsById(x)));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheHit = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
    }

    repository.deleteAllByIdInBatch(List.of(SKU, SKU2));

    for (String sku : List.of(SKU, SKU2, SKU3)) {
      var cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testDeleteDoesEvict() {
    var offering = createOffering(SKU);
    assertTrue(repository.existsById(SKU));

    var cacheHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));

    repository.delete(offering);

    var cacheMiss = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheMiss.isEmpty());
  }

  @Test
  @Transactional
  void testDeleteByIdEvicts() {
    createOffering(SKU);
    assertTrue(repository.existsById(SKU));

    var cacheHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));

    repository.deleteById(SKU);
    var cacheMiss = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheMiss.isEmpty());
  }

  @Test
  @Transactional
  void testSaveAndFlushEvictsExistenceCache() {
    assertFalse(repository.existsById(SKU));
    var cacheMiss = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertFalse(
        cacheMiss.orElseThrow(
            () -> new AssertionFailedError("Test failed: unexpected " + "cache hit!")));

    var offering =
        Offering.builder()
            .sku(SKU)
            .productName("RHEL")
            .serviceLevel(ServiceLevel.STANDARD)
            .usage(Usage.PRODUCTION)
            .build();
    repository.saveAndFlush(offering);
    assertTrue(repository.existsById(SKU));

    var cacheHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
  }

  @Test
  @Transactional
  void testSaveEvictsExistenceCache() {
    assertFalse(repository.existsById(SKU));
    var cacheMiss = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertFalse(
        cacheMiss.orElseThrow(
            () -> new AssertionFailedError("Test failed: unexpected " + "cache hit!")));

    var offering =
        Offering.builder()
            .sku(SKU)
            .productName("RHEL")
            .serviceLevel(ServiceLevel.STANDARD)
            .usage(Usage.PRODUCTION)
            .build();
    repository.save(offering);
    repository.flush();
    assertTrue(repository.existsById(SKU));

    var cacheHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(cacheHit.orElseThrow(() -> new AssertionFailedError("Test failed: cache miss!")));
  }

  @Test
  @Transactional
  void testSaveAllAndFlushEvictsExistenceCache() {
    createOffering(SKU);
    assertTrue(repository.existsById(SKU));
    var skuHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(skuHit.orElseThrow(() -> new AssertionFailedError("Test failed: SKU2 is missing")));

    assertFalse(repository.existsById(SKU2));
    var cacheMiss = getCachedOffering(SKU2, EXISTS_CACHE, Boolean.class);
    assertFalse(
        cacheMiss.orElseThrow(
            () -> new AssertionFailedError("Test failed: unexpected cache hit for SKU2")));

    var offering =
        Offering.builder()
            .sku(SKU2)
            .productName("RHEL")
            .serviceLevel(ServiceLevel.STANDARD)
            .usage(Usage.PRODUCTION)
            .build();
    repository.saveAllAndFlush(List.of(offering));

    for (String sku : List.of(SKU, SKU2)) {
      cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }

  @Test
  @Transactional
  void testSaveAllEvictsExistenceCache() {
    createOffering(SKU);
    assertTrue(repository.existsById(SKU));
    var skuHit = getCachedOffering(SKU, EXISTS_CACHE, Boolean.class);
    assertTrue(skuHit.orElseThrow(() -> new AssertionFailedError("Test failed: SKU2 is missing")));

    assertFalse(repository.existsById(SKU2));
    var cacheMiss = getCachedOffering(SKU2, EXISTS_CACHE, Boolean.class);
    assertFalse(
        cacheMiss.orElseThrow(
            () -> new AssertionFailedError("Test failed: unexpected cache hit for SKU2")));

    var offering =
        Offering.builder()
            .sku(SKU2)
            .productName("RHEL")
            .serviceLevel(ServiceLevel.STANDARD)
            .usage(Usage.PRODUCTION)
            .build();
    repository.saveAll(List.of(offering));
    repository.flush();

    for (String sku : List.of(SKU, SKU2)) {
      cacheMiss = getCachedOffering(sku, EXISTS_CACHE, Boolean.class);
      assertTrue(cacheMiss.isEmpty());
    }
  }
}
