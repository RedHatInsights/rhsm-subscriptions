package org.candlepin.subscriptions;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class TestSwatchLibrary {

    @Test
    void testFruitSaladAccessible(){
        FruitSalad fruitSalad = new FruitSalad();
        assertTrue(fruitSalad.isYummyYummy());
    }


}
