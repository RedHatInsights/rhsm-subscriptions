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
package org.candlepin.subscriptions.validator;

import static org.candlepin.subscriptions.validator.Iso8601Format.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintViolation;
import java.util.Set;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.hibernate.validator.internal.util.annotation.AnnotationDescriptor.Builder;
import org.hibernate.validator.internal.util.annotation.AnnotationFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

@ExtendWith(MockitoExtension.class)
class Iso8601ValidatorTest {
  public static final String SAMPLE_LOCAL_DATE = "2011-12-03";
  public static final String SAMPLE_OFFSET_DATE = "2011-12-03+01:00";

  public static final String SAMPLE_LOCAL_TIME = "10:15:30";
  public static final String SAMPLE_OFFSET_TIME = "10:15:30+01:00";

  public static final String SAMPLE_LOCAL_DATE_TIME = "2011-12-03T10:15:30";
  public static final String SAMPLE_OFFSET_DATE_TIME = "2011-12-03T10:15:30+01:00";
  public static final String SAMPLE_ZONED_DATE_TIME = "2011-12-03T10:15:30+01:00[Europe/Paris]";

  public static final String SAMPLE_INSTANT = "2011-12-03T10:15:30Z";

  private static class ValidThing {
    @Iso8601(ISO_LOCAL_TIME)
    private String time;

    public String getTime() {
      return time;
    }

    public void setTime(String time) {
      this.time = time;
    }
  }

  @Mock private ConstraintValidatorContext context;

  @BeforeEach
  private void setUp() {
    lenient()
        .when(context.unwrap(HibernateConstraintValidatorContext.class))
        .thenReturn(mock(HibernateConstraintValidatorContext.class));
  }

  /*
   * Please note that some of these formats overlap with others.  For example,
   * "2011-12-03T10:15:30+01:00" is both a valid ISO_DATE_TIME and a valid ISO_OFFSET_DATE_TIME. The
   * tests are constructed with this in mind.
   */
  @Test
  void testIsoDate() {
    Iso8601Validator validator = buildValidator(ISO_DATE);
    assertTrue(validator.isValid(SAMPLE_LOCAL_DATE, context));
    assertTrue(validator.isValid(SAMPLE_OFFSET_DATE, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_TIME, context));
  }

  @Test
  void testIsoLocalDate() {
    Iso8601Validator validator = buildValidator(ISO_LOCAL_DATE);
    assertTrue(validator.isValid(SAMPLE_LOCAL_DATE, context));
    assertFalse(validator.isValid(SAMPLE_OFFSET_DATE, context));
  }

  @Test
  void testIsoOffsetDate() {
    Iso8601Validator validator = buildValidator(ISO_OFFSET_DATE);
    assertTrue(validator.isValid(SAMPLE_OFFSET_DATE, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_DATE, context));
  }

  @Test
  void testIsoTime() {
    Iso8601Validator validator = buildValidator(ISO_TIME);
    assertTrue(validator.isValid(SAMPLE_LOCAL_TIME, context));
    assertTrue(validator.isValid(SAMPLE_OFFSET_TIME, context));
    assertFalse(validator.isValid(SAMPLE_INSTANT, context));
  }

  @Test
  void testIsoLocalTime() {
    Iso8601Validator validator = buildValidator(ISO_LOCAL_TIME);
    assertTrue(validator.isValid(SAMPLE_LOCAL_TIME, context));
    assertFalse(validator.isValid(SAMPLE_OFFSET_TIME, context));
  }

  @Test
  void testIsoOffsetTime() {
    Iso8601Validator validator = buildValidator(ISO_OFFSET_TIME);
    assertTrue(validator.isValid(SAMPLE_OFFSET_TIME, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_TIME, context));
  }

  @Test
  void testIsoDateTime() {
    Iso8601Validator validator = buildValidator(ISO_DATE_TIME);
    assertTrue(validator.isValid(SAMPLE_LOCAL_DATE_TIME, context));
    assertTrue(validator.isValid(SAMPLE_OFFSET_DATE_TIME, context));
    assertTrue(validator.isValid(SAMPLE_ZONED_DATE_TIME, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_TIME, context));
  }

  @Test
  void testIsoLocalDateTime() {
    Iso8601Validator validator = buildValidator(ISO_LOCAL_DATE_TIME);
    assertTrue(validator.isValid(SAMPLE_LOCAL_DATE_TIME, context));
    assertFalse(validator.isValid(SAMPLE_OFFSET_DATE_TIME, context));
  }

  @Test
  void testIsoOffsetDateTime() {
    Iso8601Validator validator = buildValidator(ISO_OFFSET_DATE_TIME);
    assertTrue(validator.isValid(SAMPLE_OFFSET_DATE_TIME, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_DATE_TIME, context));
  }

  @Test
  void testIsoZonedDateTime() {
    Iso8601Validator validator = buildValidator(ISO_ZONED_DATE_TIME);
    assertTrue(validator.isValid(SAMPLE_ZONED_DATE_TIME, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_DATE_TIME, context));
  }

  @Test
  void testIsoInstant() {
    Iso8601Validator validator = buildValidator(ISO_INSTANT);
    assertTrue(validator.isValid(SAMPLE_INSTANT, context));
    assertFalse(validator.isValid(SAMPLE_LOCAL_DATE_TIME, context));
  }

  @Test
  void testValidatesNull() {
    // Note that the Jakarta Bean Validation specification recommends to consider null values as
    // being
    // valid. If null is not a valid value for an element, it should be annotated with @NotNull
    // explicitly
    Iso8601Validator validator = buildValidator(ISO_INSTANT);
    assertTrue(validator.isValid(null, context));
  }

  @Test
  void testSimpleValidation() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    ValidThing thing = new ValidThing();
    thing.setTime("12:30");

    Set<ConstraintViolation<ValidThing>> result = validator.validate(thing);
    assertEquals(0, result.size());
  }

  @Test
  void testBuildsCorrectErrorMessage() {
    String time = "ZZZ";
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();

    ValidThing thing = new ValidThing();
    thing.setTime(time);

    Set<ConstraintViolation<ValidThing>> constraintViolations = validator.validate(thing);
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    String expected =
        String.format(
            "The string \"%s\" must be in ISO 8601 format similar to %s.",
            time, ISO_LOCAL_TIME.example);
    assertEquals(expected, violation.getMessage());
  }

  private Iso8601Validator buildValidator(Iso8601Format format) {
    Builder<Iso8601> builder = new Builder<>(Iso8601.class);
    builder.setAttribute("value", format);
    Iso8601Validator validator = new Iso8601Validator();
    validator.initialize(AnnotationFactory.create(builder.build()));
    return validator;
  }
}
