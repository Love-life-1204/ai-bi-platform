package com.aibi.platform.analytics.dto;

import com.aibi.platform.common.json.JsonResourceId;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** P2 高级分析的无 SQL 公共契约。 */
public final class AnalyticsDtos {

    private AnalyticsDtos() {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record TimePoint(@NotNull Instant timestamp, double value) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record AnomalyRequest(
            @Positive @JsonResourceId long metricVersionId,
            @NotEmpty @Size(max = 10000) List<@NotNull @Valid TimePoint> points,
            Double lowerThreshold,
            Double upperThreshold,
            @DecimalMin("1.0") @DecimalMax("10.0") double zScoreThreshold,
            @Min(3) @Max(365) int rollingWindow) {

        public AnomalyRequest {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    public record AnomalyPoint(
            Instant timestamp, double value, boolean anomalous,
            List<String> reasons, Double baseline, Double standardDeviation,
            Double lowerBand, Double upperBand) {
    }

    public record AnomalyResult(@JsonResourceId long metricVersionId, int anomalyCount,
            List<AnomalyPoint> points) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record WindowRequest(
            @Positive @JsonResourceId long metricVersionId,
            @NotEmpty @Size(max = 10000) List<@NotNull @Valid TimePoint> points,
            @Min(3) @Max(5000) int trainingSize,
            @Min(1) @Max(1000) int validationSize,
            @Min(1) @Max(1000) int step) {

        public WindowRequest {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    public record TrainingWindow(int sequence, int trainingStartIndex, int trainingEndIndex,
            int validationStartIndex, int validationEndIndex,
            Instant trainingStart, Instant trainingEnd,
            Instant validationStart, Instant validationEnd) {
    }

    public record WindowPlan(@JsonResourceId long metricVersionId, int pointCount,
            List<TrainingWindow> windows) {
    }

    @JsonIgnoreProperties(ignoreUnknown = false)
    public record ForecastRequest(
            @Positive @JsonResourceId long metricVersionId,
            @NotEmpty @Size(max = 10000) List<@NotNull @Valid TimePoint> points,
            @Min(1) @Max(365) int horizon,
            @Min(0) @Max(365) int seasonLength,
            @Min(3) @Max(5000) int trainingSize,
            @Min(1) @Max(1000) int validationSize,
            @DecimalMin("0.8") @DecimalMax("0.99") double confidenceLevel) {

        public ForecastRequest {
            points = points == null ? List.of() : List.copyOf(points);
        }
    }

    public record BacktestMetrics(int sampleCount, double mae, double rmse,
            Double mape, double intervalCoverage) {
    }

    public record ForecastPoint(Instant timestamp, double predicted,
            double lower, double upper, double confidenceLevel) {
    }

    public record ForecastResult(@JsonResourceId long metricVersionId, String model,
            TrainingWindow trainingWindow, BacktestMetrics backtest,
            List<ForecastPoint> forecast) {
    }
}
