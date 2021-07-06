package org.candlepin.subscriptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FruitSaladTest {

    @Test
    void testSanity() {
        assertTrue(true);
    }

    @Test
    void testIsYummyYummy() {
        FruitSalad fruitSalad = new FruitSalad();
        assertTrue(fruitSalad.isYummyYummy());
    }

    @Test
    void testNegativeIsYummyYummy() {
        FruitSalad fruitSalad = new FruitSalad();
        assertFalse(!fruitSalad.isYummyYummy());
    }

}