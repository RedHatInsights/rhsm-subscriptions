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

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.constraintvalidation.SupportedValidationTarget;
import jakarta.validation.constraintvalidation.ValidationTarget;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.OffsetTime;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoField;
import java.time.temporal.Temporal;
import java.time.temporal.TemporalQuery;
import org.hibernate.validator.constraintvalidation.HibernateConstraintValidatorContext;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;

/**
 * Validator implementation that verifies the difference between two ISO 8601 strings does not
 * exceed a given Duration. The validator operates on the last 2 string parameters of a method and
 * assumes that the begin parameter precedes the end parameter.
 */
@SupportedValidationTarget(ValidationTarget.PARAMETERS)
public class ParameterDurationValidator
    implements ConstraintValidator<ParameterDuration, Object[]>, BeanFactoryAware {

  private BeanFactory beanFactory;
  private Duration maxDuration;
  private Iso8601Format format;

  /**
   * Initialize this instance. See {@link ParameterDurationValidator#findDuration(String, String,
   * Iso8601Format)} for a discussion on why certain formats are not supported.
   *
   * @param constraintAnnotation the annotation instance
   */
  @Override
  public void initialize(ParameterDuration constraintAnnotation) {
    format = constraintAnnotation.format();

    switch (format) {
      case ISO_DATE:
      case ISO_TIME:
      case ISO_DATE_TIME:
      case ISO_OFFSET_DATE:
        throw new IllegalArgumentException(
            format.name() + " is not a supported ISO 8601 format for" + " this validator");
      default:
        break;
    }

    ExpressionParser parser = new SpelExpressionParser();
    StandardEvaluationContext context = new StandardEvaluationContext();
    if (beanFactory != null) {
      context.setBeanResolver(new BeanFactoryResolver(beanFactory));
    }
    Expression expression = parser.parseExpression(constraintAnnotation.value());
    maxDuration = expression.getValue(context, Duration.class);
  }

  @Override
  public boolean isValid(Object[] value, ConstraintValidatorContext context) {
    if (value.length < 2) {
      throw new IllegalArgumentException("At least 2 arguments are required");
    }
    String beginText = (String) value[value.length - 2];
    String endText = (String) value[value.length - 1];

    // leave null-checking to @NotNull on individual parameters
    if (beginText == null || endText == null) {
      return true;
    }

    HibernateConstraintValidatorContext hibernateContext =
        context.unwrap(HibernateConstraintValidatorContext.class);
    hibernateContext
        .addMessageParameter("begin", beginText)
        .addMessageParameter("end", endText)
        .addMessageParameter("duration", maxDuration.toHours());

    Duration actual;
    try {
      actual = findDuration(beginText, endText, format);
    } catch (DateTimeParseException e) {
      // If I were a better person, this message would indicate exactly which string failed to meet
      // format requirements. We could do that by refactoring findDuration to return a Temporal,
      // validate each Temporal before finding the Duration, and add a ConstraintViolation for each
      // string that doesn't parse.  That design would make this method a lot longer and require a
      // more knowledge of the validation API than what I have.  Nevertheless, it's worth
      // considering
      // as an improvement if the error messaging becomes a pain point.
      hibernateContext
          .addMessageParameter("example", format.example)
          .buildConstraintViolationWithTemplate(
              "The start time, {begin}, and end time, {end}, must"
                  + " conform to a format like {example}")
          .addConstraintViolation()
          .disableDefaultConstraintViolation();
      return false;
    }

    if (actual.isNegative()) {
      hibernateContext
          .buildConstraintViolationWithTemplate(
              "The start time, {begin}, can not occur after the " + "ending time, {end}")
          .addConstraintViolation()
          .disableDefaultConstraintViolation();
      return false;
    }

    return maxDuration.compareTo(actual) >= 0;
  }

  /**
   * Take two parameters, parse them according to the formatter given by an {@link Iso8601Format}
   * parameter and return the duration between them.
   *
   * <p>Not all {@link Iso8601Format}'s are supported. Specifically, formats like ISO_OFFSET_DATE,
   * ISO_DATE, ISO_TIME, and ISO_DATE_TIME. In the case of ISO_TIME, ISO_DATE, and ISO_DATE_TIME
   * those formats have optional components that make comparison difficult. For example,
   * "10:15-01:00" and "12:45" are both valid ISO_TIME formats, but comparing the two is meaningless
   * since one has an offset and the other does not. ISO_OFFSET_DATE is a rather odd format in that
   * it is not actually part of the ISO 8601 standard! It is also problematic for comparisons so it
   * is not supported here.
   *
   * <p>A note on using date only format: the duration between those two dates will be calculated
   * from midnight on each day. This will not take into account any changes around Daylight Saving
   * Time.
   *
   * @param beginText a String parseable by the DateTimeFormatter given with the format parameter
   * @param endText a String parseable by the DateTimeFormatter given with the format parameter
   * @param format a value of Iso8601Format with an associated DateTimeFormatter
   * @throws DateTimeParseException if the beginText or endText cannot be parsed by the method
   * @return the Duration between beginText and endText
   */
  protected static Duration findDuration(String beginText, String endText, Iso8601Format format) {
    DateTimeFormatter formatter = format.formatter;
    TemporalQuery<Temporal> query;
    switch (format) {
      case ISO_LOCAL_DATE_TIME:
        query = LocalDateTime::from;
        break;
      case ISO_OFFSET_DATE_TIME:
        query = OffsetDateTime::from;
        break;
      case ISO_ZONED_DATE_TIME:
        query = ZonedDateTime::from;
        break;
      case ISO_INSTANT:
        query = Instant::from;
        break;
      case ISO_LOCAL_TIME:
        query = LocalTime::from;
        break;
      case ISO_OFFSET_TIME:
        query = OffsetTime::from;
        break;
      case ISO_LOCAL_DATE:
        query = LocalDateTime::from;
        formatter =
            new DateTimeFormatterBuilder()
                // date and offset
                .append(DateTimeFormatter.ISO_LOCAL_DATE)
                // default values for hour and minute
                .parseDefaulting(ChronoField.HOUR_OF_DAY, 0)
                .parseDefaulting(ChronoField.MINUTE_OF_HOUR, 0)
                .toFormatter();
        break;
      default:
        throw new IllegalArgumentException("Unsupported ISO 8601 format: " + format);
    }

    Temporal begin = formatter.parse(beginText, query);
    Temporal end = formatter.parse(endText, query);
    return Duration.between(begin, end);
  }

  @Override
  public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
    this.beanFactory = beanFactory;
  }
}
