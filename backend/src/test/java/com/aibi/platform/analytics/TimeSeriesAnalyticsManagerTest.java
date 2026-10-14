package com.aibi.platform.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.TimePoint;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowRequest;
import com.aibi.platform.analytics.manager.TimeSeriesAnalyticsManager;
import com.aibi.platform.common.exception.BadRequestException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class TimeSeriesAnalyticsManagerTest {

    private final TimeSeriesAnalyticsManager manager = new TimeSeriesAnalyticsManager();

    @Test
    void combinesBusinessThresholdAndRollingStatisticalDetection() {
        List<TimePoint> points = points(10, 11, 9, 10, 50);
        var result = manager.detect(new AnomalyRequest(7, points, 0.0, 40.0, 2.0, 4));

        assertThat(result.anomalyCount()).isEqualTo(1);
        assertThat(result.points().get(4).reasons())
                .containsExactly("ABOVE_BUSINESS_THRESHOLD", "ROLLING_Z_SCORE");
        assertThat(result.points().get(4).baseline()).isEqualTo(10.0);
    }

    @Test
    void createsWalkForwardWindowsWithoutFutureLeakage() {
        var plan = manager.windows(new WindowRequest(7, points(1, 2, 3, 4, 5, 6, 7), 3, 2, 2));

        assertThat(plan.windows()).hasSize(2);
        assertThat(plan.windows().get(0).trainingEndIndex()).isLessThan(
                plan.windows().get(0).validationStartIndex());
        assertThat(plan.windows().get(1).validationEndIndex()).isEqualTo(6);
    }

    @Test
    void linearSeriesBacktestAndForecastAreReproducible() {
        var result = manager.forecast(new ForecastRequest(
                7, points(1, 2, 3, 4, 5, 6, 7, 8), 2, 0, 5, 2, 0.95));

        assertThat(result.backtest().mae()).isLessThan(0.000001);
        assertThat(result.backtest().rmse()).isLessThan(0.000001);
        assertThat(result.forecast()).extracting(value -> value.predicted())
                .containsExactly(9.0, 10.0);
        assertThat(result.forecast()).allSatisfy(value -> {
            assertThat(value.lower()).isLessThanOrEqualTo(value.predicted());
            assertThat(value.upper()).isGreaterThanOrEqualTo(value.predicted());
        });
    }

    @Test
    void rejectsInvalidOrInsufficientWindows() {
        assertThatThrownBy(() -> manager.windows(
                new WindowRequest(7, points(1, 2, 3), 3, 1, 1)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void seasonalForecastRecursivelyCarriesPatternBeyondFirstSeason() {
        var result = manager.forecast(new ForecastRequest(
                7, points(10, 20, 10, 20, 10, 20), 4, 2, 4, 2, 0.9));

        assertThat(result.forecast()).extracting(value -> value.predicted())
                .containsExactly(10.0, 20.0, 10.0, 20.0);
    }

    private List<TimePoint> points(double... values) {
        Instant start = Instant.parse("2026-01-01T00:00:00Z");
        List<TimePoint> output = new ArrayList<>();
        for (int index = 0; index < values.length; index++) {
            output.add(new TimePoint(start.plus(index, ChronoUnit.DAYS), values[index]));
        }
        return List.copyOf(output);
    }
}
