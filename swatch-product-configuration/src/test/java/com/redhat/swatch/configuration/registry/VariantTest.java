package com.redhat.swatch.configuration.registry;

import static org.junit.jupiter.api.Assertions.*;

import lombok.SneakyThrows;
import org.junit.jupiter.api.Test;

class VariantTest {

  @SneakyThrows
  @Test
  void testFindByRole() {

    var variant = Variant.findByRole("Red Hat Enterprise Linux Server");

    var expected = "RHEL Server";
    var actual = variant.get().getTag();

    assertEquals(expected, actual);
  }
}
