/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.analytics.event.data;

import static org.apache.commons.collections4.CollectionUtils.isNotEmpty;
import static org.apache.commons.lang3.StringUtils.EMPTY;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.hisp.dhis.analytics.event.data.JdbcEventAnalyticsManager.OPEN_IN;
import static org.hisp.dhis.common.IdentifiableObjectUtils.getUids;
import static org.hisp.dhis.commons.util.TextUtils.getQuotedCommaDelimitedString;
import static org.hisp.dhis.system.util.SqlUtils.quote;
import static org.hisp.dhis.util.DateUtils.getMediumDateString;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Builder;
import lombok.Data;
import org.hisp.dhis.analytics.EventOutputType;
import org.hisp.dhis.analytics.TimeField;
import org.hisp.dhis.analytics.event.EventQueryParams;
import org.hisp.dhis.common.DateRange;
import org.hisp.dhis.common.DimensionalItemObject;
import org.hisp.dhis.jdbc.PostgreSqlStatementBuilder;
import org.hisp.dhis.jdbc.StatementBuilder;
import org.hisp.dhis.period.Period;
import org.hisp.dhis.period.PeriodType;

/** Provides methods targeting the generation of SQL statements for periods and time fields. */
public abstract class TimeFieldSqlRenderer {

  protected final StatementBuilder statementBuilder = new PostgreSqlStatementBuilder();

  /**
   * Generates a SQL statement for periods or time field based on the given params.
   *
   * @param params the {@link EventQueryParams}
   * @return the SQL statement
   */
  public String renderPeriodTimeFieldSql(EventQueryParams params) {
    StringBuilder sql = new StringBuilder();

    if (params.hasNonDefaultBoundaries()) {
      sql.append(getConditionForNonDefaultBoundaries(params));
    } else if (params.useStartEndDates() || params.hasTimeDateRanges()) {
      sql.append(getDateRangeCondition(params));
    } else // Periods condition only for pivot table (aggregated).
    {
      sql.append(getAggregatedConditionForPeriods(params));
    }

    if (isEmpty(sql)) {
      return sql.toString();
    }

    return "(" + sql + ") ";
  }

  /**
   * Checks if the given time field is allowed in time/date queries.
   *
   * @param timeField the {@link TimeField}
   * @return true if the time field is allowed, false otherwise
   */
  private boolean isAllowed(TimeField timeField) {
    return getAllowedTimeFields().contains(timeField);
  }

  /**
   * Returns a SQL statement based on start/end dates and all time/date ranges defined, if any.
   *
   * @param params the {@link EventQueryParams}
   * @return the SQL statement
   */
  private String getDateRangeCondition(EventQueryParams params) {
    List<String> orConditions = new ArrayList<>();

    if (params.hasStartEndDate()) {
      addStartEndDateToCondition(params, orConditions);
    }

    params
        .getTimeDateRanges()
        .forEach(
            (timeField, dateRanges) -> {
              if (params.hasContinuousRange(dateRanges)) {
                // Picks the start date of the first range and end date of the last range.
                DateRange dateRange =
                    new DateRange(
                        dateRanges.get(0).getStartDate(),
                        dateRanges.get(dateRanges.size() - 1).getEndDate());

                ColumnWithDateRange columnWithDateRange =
                    ColumnWithDateRange.of(
                        getColumnName(Optional.of(timeField), params.getOutputType()), dateRange);
                orConditions.add(getDateRangeCondition(columnWithDateRange));
              } else {
                dateRanges.forEach(
                    dateRange -> {
                      ColumnWithDateRange columnWithDateRange =
                          ColumnWithDateRange.of(
                              getColumnName(Optional.of(timeField), params.getOutputType()),
                              dateRange);
                      orConditions.add(getDateRangeCondition(columnWithDateRange));
                    });
              }
            });

    return isNotEmpty(orConditions) ? String.join(" or ", orConditions) : EMPTY;
  }

  /**
   * Returns a string representing the SQL condition for the given {@link ColumnWithDateRange}.
   *
   * @param dateRangeColumn
   * @return the SQL statement
   */
  private String getDateRangeCondition(ColumnWithDateRange dateRangeColumn) {
    return "("
        + dateRangeColumn.getColumn()
        + " >= '"
        + getMediumDateString(dateRangeColumn.getDateRange().getStartDate())
        + "' and "
        + dateRangeColumn.getColumn()
        + " < '"
        + getMediumDateString(dateRangeColumn.getDateRange().getEndDatePlusOneDay())
        + "')";
  }

  /**
   * Adds the default data range condition into the given list of conditions.
   *
   * @param params the {@link EventQueryParams}
   * @param conditions a list of SQL conditions
   */
  private void addStartEndDateToCondition(EventQueryParams params, List<String> conditions) {
    ColumnWithDateRange defaultDateRangeColumn =
        ColumnWithDateRange.builder()
            .column(getColumnName(getTimeField(params), params.getOutputType()))
            .dateRange(new DateRange(params.getStartDate(), params.getEndDate()))
            .build();

    conditions.add(getDateRangeCondition(defaultDateRangeColumn));
  }

  /**
   * Returns the time field {@link TimeField} set in the given params {@link EventQueryParams}. It
   * also checks if the time field set is allowed. Case negative, it returns an empty optional.
   *
   * @param params the {@link EventQueryParams}
   * @return and optional {@link TimeField}
   */
  protected Optional<TimeField> getTimeField(EventQueryParams params) {
    return TimeField.of(params.getTimeField()).filter(this::isAllowed);
  }

  protected boolean isPeriod(DimensionalItemObject dimensionalItemObject) {
    return dimensionalItemObject instanceof Period;
  }

  @Data
  @Builder
  private static class ColumnWithDateRange {
    private final String column;

    private final DateRange dateRange;

    static ColumnWithDateRange of(String column, DateRange dateRange) {
      return ColumnWithDateRange.builder().column(column).dateRange(dateRange).build();
    }
  }

  /**
   * It generates a period SQL statement for aggregated queries based on the given params.
   *
   * @param params {@link EventQueryParams}
   * @return the SQL statement
   */
  protected abstract String getAggregatedConditionForPeriods(EventQueryParams params);

  /**
   * Returns the field/column associated with the given {@link TimeField}. If the time field is
   * empty, it will return a default one. The {@link EventOutputType} might be used to decided which
   * field/column will be the default.
   *
   * @param timeField the optional {@link TimeField}
   * @param outputType the {@link EventOutputType}
   * @return the field/column of the given time field
   */
  protected abstract String getColumnName(
      Optional<TimeField> timeField, EventOutputType outputType);

  /**
   * Generates a SQL statement based on program indicators boundaries.
   *
   * @param params {@link EventQueryParams}
   * @return the SQL statement
   */
  protected abstract String getConditionForNonDefaultBoundaries(EventQueryParams params);

  /**
   * Simply returns a collection of {@link TimeField} allowed for the respective implementation.
   *
   * @return the collection of {@link TimeField}
   */
  protected abstract Set<TimeField> getAllowedTimeFields();

  /**
   * Returns a SQL statement for the given {@link DimensionalItemObject} list. {@link
   * DimensionalItemObject} are of type {@link Period}. This method renders SQL condition to match
   * the given periods, considering their different {@link PeriodType}.<br>
   * <br>
   * Example (different period types)<br>
   * <br>
   * {@code ('daily' in ('20200111', '20210211') or 'monthly' in ('202001', '202002'))}<br>
   * <br>
   * Example (same period type)<br>
   * <br>
   * {@code 'monthly' in ('202001', '202002')}
   *
   * @param alias
   * @param periods
   * @return
   */
  protected String getSqlForAllPeriods(String alias, List<DimensionalItemObject> periods) {
    StringBuilder sql = new StringBuilder();

    Map<PeriodType, List<Period>> periodsByType =
        periods.stream()
            .map(Period.class::cast)
            .collect(Collectors.groupingBy(Period::getPeriodType));

    Collection<String> periodSingleConditions =
        periodsByType.entrySet().stream().map(entry -> toSqlCondition(alias, entry)).toList();

    String periodsCondition = wrapAndJoinWithOrIfNecessary(periodSingleConditions);

    if (periodsByType.size() > 1) {
      sql.append(" (").append(periodsCondition).append(")");
    } else {
      sql.append(periodsCondition);
    }
    return sql.toString();
  }

  private String wrapAndJoinWithOrIfNecessary(Collection<String> periodSingleConditions) {
    if (periodSingleConditions.size() > 1) {
      return periodSingleConditions.stream().collect(Collectors.joining(" or ", "(", ")"));
    }
    return periodSingleConditions.stream().findFirst().orElse(EMPTY);
  }

  private String toSqlCondition(String alias, Entry<PeriodType, List<Period>> entry) {
    String columnName = entry.getKey().getName().toLowerCase();
    return quote(alias, columnName)
        + OPEN_IN
        + getQuotedCommaDelimitedString(getUids(entry.getValue()))
        + ") ";
  }
}
