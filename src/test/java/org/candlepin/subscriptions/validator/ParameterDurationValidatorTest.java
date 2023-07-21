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

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.instanceOf;
import static org.junit.jupiter.api.Assertions.*;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.ValidationException;
import java.time.Duration;
import java.util.Set;
import org.hibernate.validator.internal.util.annotation.AnnotationDescriptor.Builder;
import org.hibernate.validator.internal.util.annotation.AnnotationFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.expression.spel.SpelEvaluationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.validation.annotation.Validated;

@SpringBootTest
@ActiveProfiles({"worker", "test"})
class ParameterDurationValidatorTest {
  public static final int VALID_HOURS = 24;
  public static final String BEGIN_DATE_TIME = "2011-12-03T10:15Z";
  public static final String END_DATE_TIME = "2011-12-03T12:45Z";

  @Autowired BeanFactory beanFactory;
  @Autowired ValidatedThing validatedThing;

  @TestConfiguration
  static class ParameterDurationValidatorTestConfig {
    @Bean
    Duration testDuration() {
      return Duration.ofHours(VALID_HOURS);
    }

    @Bean
    String badBean() {
      return "This is a bean not suitable for use as a duration";
    }

    @Bean
    ValidatedThing validatedThing() {
      return new ValidatedThing();
    }
  }

  @Validated
  static class ValidatedThing {
    @ParameterDuration("@testDuration")
    public boolean myMethod(String begin, String end) {
      return true;
    }

    @ParameterDuration("@testDuration")
    public boolean badMethodSignature(String begin) {
      return true;
    }

    @ParameterDuration("@badBean")
    public boolean badBean(String begin, String end) {
      return true;
    }

    @ParameterDuration("@noBean")
    public boolean noBean(String begin, String end) {
      return true;
    }

    @ParameterDuration(value = "@testDuration", format = Iso8601Format.ISO_INSTANT)
    public boolean myInstantMethod(String begin, String end) {
      return true;
    }

    @ParameterDuration(value = "@testDuration", format = Iso8601Format.ISO_LOCAL_DATE)
    public boolean myLocalDateMethod(String begin, String end) {
      return true;
    }

    @ParameterDuration(value = "@testDuration", format = Iso8601Format.ISO_LOCAL_TIME)
    public boolean myLocalTimeMethod(String begin, String end) {
      return true;
    }

    @ParameterDuration(value = "@testDuration", format = Iso8601Format.ISO_DATE_TIME)
    public boolean unsupportedIsoFormat(String begin, String end) {
      return true;
    }
  }

  @Test
  void testValidatesLocalDateTimeDuration() {
    assertTrue(validatedThing.myMethod(BEGIN_DATE_TIME, END_DATE_TIME));
  }

  @Test
  void testDoesNotValidateTooLongLocalDateTimeDuration() {
    String begin = BEGIN_DATE_TIME;
    String longEnd = "2019-01-07T10:15Z";
    var ex =
        assertThrows(
            ConstraintViolationException.class, () -> validatedThing.myMethod(begin, longEnd));
    Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    String expected =
        String.format(
            "The start time, %s, can not be more than %s hours from the end time, %s",
            begin, VALID_HOURS, longEnd);
    assertEquals(expected, violation.getMessage());
  }

  @Test
  void testDoesNotInitializeWithUnsupportedIsoFormat() {
    var ex =
        assertThrows(
            ValidationException.class,
            () -> validatedThing.unsupportedIsoFormat(BEGIN_DATE_TIME, END_DATE_TIME));
    var cause = ex.getCause();
    assertThat(cause, instanceOf(IllegalArgumentException.class));
    assertEquals(
        "ISO_DATE_TIME is not a supported ISO 8601 format for this validator", cause.getMessage());
  }

  @Test
  void validatesBadFormat() {
    String badBegin = "2011-12-03TZ";
    String badEnd = "2011-12-02TZ";
    String example = Iso8601Format.ISO_OFFSET_DATE_TIME.example;
    var ex =
        assertThrows(
            ConstraintViolationException.class,
            () -> {
              validatedThing.myMethod(badBegin, badEnd);
            });
    Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    String expected =
        String.format(
            "The start time, %s, and end time, %s, must conform to a format like %s",
            badBegin, badEnd, example);
    assertEquals(expected, violation.getMessage());
  }

  @Test
  void testValidatesBeginBeforeEnd() {
    String negativeEnd = "2011-12-02T21:45Z";
    var ex =
        assertThrows(
            ConstraintViolationException.class,
            () -> {
              validatedThing.myMethod(BEGIN_DATE_TIME, negativeEnd);
            });
    Set<ConstraintViolation<?>> constraintViolations = ex.getConstraintViolations();
    assertEquals(1, constraintViolations.size());
    var violation = constraintViolations.stream().findFirst().get();
    String expected =
        String.format(
            "The start time, %s, can not occur after the ending time, %s",
            BEGIN_DATE_TIME, negativeEnd);
    assertEquals(expected, violation.getMessage());
  }

  @Test
  void testValidatesInstantDuration() {
    assertTrue(
        validatedThing.myInstantMethod("2011-12-03T10:15:30.000Z", "2011-12-03T12:45:30.000Z"));
    assertTrue(validatedThing.myInstantMethod("2011-12-03T10:15:30Z", "2011-12-03T12:45:30Z"));
  }

  @Test
  void testValidatesTimeOnly() {
    assertTrue(validatedThing.myLocalTimeMethod("10:15", "12:45"));
  }

  @Test
  void testValidatesDatesOnly() {
    assertTrue(validatedThing.myLocalDateMethod("2011-12-03", "2011-12-04"));
  }

  @Test
  void testDoesNotWorryAboutNull() {
    assertTrue(validatedThing.myMethod(null, BEGIN_DATE_TIME));
  }

  @Test
  void testRequiresTwoParameters() {
    var ex =
        assertThrows(
            ValidationException.class, () -> validatedThing.badMethodSignature(BEGIN_DATE_TIME));
    var cause = ex.getCause();
    assertThat(cause, instanceOf(IllegalArgumentException.class));
    assertEquals("At least 2 arguments are required", cause.getMessage());
  }

  @Test
  void testBadBean() {
    var ex =
        assertThrows(
            ValidationException.class,
            () -> validatedThing.badBean(BEGIN_DATE_TIME, END_DATE_TIME));
    var cause = ex.getCause();
    assertThat(cause, instanceOf(SpelEvaluationException.class));
  }

  @Test
  void testNoBean() {
    var ex =
        assertThrows(
            ValidationException.class, () -> validatedThing.noBean(BEGIN_DATE_TIME, END_DATE_TIME));
    var cause = ex.getCause();
    assertThat(cause, instanceOf(SpelEvaluationException.class));
  }

  /* These are primarily to illustrate another way of doing the validator testing if you do not need to
   * provide a ConstraintValidationContext.  Creating a placeholder ConstraintValidationContext is not trivial,
   * so most of the tests that would dereference the context call the Validator indirectly by invoking a
   * method it's been applied to.  The ConstraintValidationContext could be mocked for cases that don't
   * use it, but for testing that the proper error messages are constructed we really don't want to use a
   * mock for that.
   *
   * I'm leaving these methods here as examples for others in the future as it took some doing to figure
   * out how to properly create the annotation instance to pass to the validator's initialize method.
   */
  private ParameterDurationValidator buildValidator(String spelExpression) {
    Builder<ParameterDuration> builder = new Builder<>(ParameterDuration.class);
    builder.setAttribute("value", spelExpression);
    return buildValidator(builder);
  }

  private ParameterDurationValidator buildValidator(String spelExpression, Iso8601Format format) {
    Builder<ParameterDuration> builder = new Builder<>(ParameterDuration.class);
    builder.setAttribute("value", spelExpression);
    builder.setAttribute("format", format);
    return buildValidator(builder);
  }

  private ParameterDurationValidator buildValidator(Builder<ParameterDuration> builder) {
    ParameterDurationValidator validator = new ParameterDurationValidator();
    validator.setBeanFactory(beanFactory);
    validator.initialize(AnnotationFactory.create(builder.build()));
    return validator;
  }

  @Test
  void testIsoLocalDateTimeDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "2011-12-03T10:15:30", "2011-12-03T12:15:30", Iso8601Format.ISO_LOCAL_DATE_TIME);
    Duration expected = Duration.ofHours(2);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoOffsetDateTimeDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "2011-12-03T10:00-01:00", // UTC 11:00
            "2011-12-03T20:00+03:00", // UTC 17:00
            Iso8601Format.ISO_OFFSET_DATE_TIME);
    Duration expected = Duration.ofHours(6);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoZonedDateTimeDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "2011-12-03T10:15:30+01:00[Europe/Paris]", // UTC 09:15:30
            "2011-12-03T12:15:30Z",
            Iso8601Format.ISO_ZONED_DATE_TIME);
    Duration expected = Duration.ofHours(3);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoInstantDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "2011-12-03T10:15:30Z", "2011-12-03T12:15:30Z", Iso8601Format.ISO_INSTANT);
    Duration expected = Duration.ofHours(2);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoLocalTimeDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration("10:15", "12:45", Iso8601Format.ISO_LOCAL_TIME);
    Duration expected = Duration.ofMinutes(150);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoOffsetTimeDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "10:00-04:00", // UTC 14:00
            "20:00+03:00", // UTC 17:00
            Iso8601Format.ISO_OFFSET_TIME);
    Duration expected = Duration.ofHours(3);
    assertEquals(expected, actual);
  }

  @Test
  void testIsoLocalDateDuration() {
    Duration actual =
        ParameterDurationValidator.findDuration(
            "2011-12-03", "2011-12-10", Iso8601Format.ISO_LOCAL_DATE);
    Duration expected = Duration.ofHours(7 * 24);
    assertEquals(expected, actual);
  }
}
