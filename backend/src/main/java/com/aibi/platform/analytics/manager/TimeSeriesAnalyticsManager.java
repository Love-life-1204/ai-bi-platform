package com.aibi.platform.analytics.manager;

import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyPoint;
import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.AnomalyResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.BacktestMetrics;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastPoint;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastRequest;
import com.aibi.platform.analytics.dto.AnalyticsDtos.ForecastResult;
import com.aibi.platform.analytics.dto.AnalyticsDtos.TimePoint;
import com.aibi.platform.analytics.dto.AnalyticsDtos.TrainingWindow;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowPlan;
import com.aibi.platform.analytics.dto.AnalyticsDtos.WindowRequest;
import com.aibi.platform.common.exception.BadRequestException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Component;

/** 可复现的本地统计基线；不调用外部模型，也不接触租户原始数据。 */
@Component
public class TimeSeriesAnalyticsManager {

    public AnomalyResult detect(AnomalyRequest request) {
        List<TimePoint> points = ordered(request.points());
        validateThresholds(request.lowerThreshold(), request.upperThreshold());
        List<AnomalyPoint> output = new ArrayList<>();
        int count = 0;
        for (int index = 0; index < points.size(); index++) {
            TimePoint point = points.get(index);
            List<String> reasons = new ArrayList<>();
            if (request.lowerThreshold() != null && point.value() < request.lowerThreshold()) {
                reasons.add("BELOW_BUSINESS_THRESHOLD");
            }
            if (request.upperThreshold() != null && point.value() > request.upperThreshold()) {
                reasons.add("ABOVE_BUSINESS_THRESHOLD");
            }
            int start = Math.max(0, index - request.rollingWindow());
            Stats stats = index - start >= 3 ? stats(points, start, index) : null;
            Double lower = null;
            Double upper = null;
            if (stats != null) {
                lower = stats.mean() - request.zScoreThreshold() * stats.standardDeviation();
                upper = stats.mean() + request.zScoreThreshold() * stats.standardDeviation();
                if (stats.standardDeviation() > 0
                        && Math.abs(point.value() - stats.mean())
                        > request.zScoreThreshold() * stats.standardDeviation()) {
                    reasons.add("ROLLING_Z_SCORE");
                }
            }
            boolean anomalous = !reasons.isEmpty();
            count += anomalous ? 1 : 0;
            output.add(new AnomalyPoint(point.timestamp(), point.value(), anomalous,
                    List.copyOf(reasons), stats == null ? null : stats.mean(),
                    stats == null ? null : stats.standardDeviation(), lower, upper));
        }
        return new AnomalyResult(request.metricVersionId(), count, List.copyOf(output));
    }

    public WindowPlan windows(WindowRequest request) {
        List<TimePoint> points = ordered(request.points());
        List<TrainingWindow> windows = buildWindows(points, request.trainingSize(),
                request.validationSize(), request.step());
        if (windows.isEmpty()) {
            throw new BadRequestException("数据量不足以形成训练窗口和验证窗口");
        }
        return new WindowPlan(request.metricVersionId(), points.size(), windows);
    }

    public ForecastResult forecast(ForecastRequest request) {
        List<TimePoint> points = ordered(request.points());
        if (request.trainingSize() + request.validationSize() > points.size()) {
            throw new BadRequestException("数据量不足以完成训练与回测");
        }
        if (request.seasonLength() > 0 && request.seasonLength() >= request.trainingSize()) {
            throw new BadRequestException("季节长度必须小于训练窗口");
        }
        int validationStart = points.size() - request.validationSize();
        int trainingStart = Math.max(0, validationStart - request.trainingSize());
        List<Double> errors = new ArrayList<>();
        List<Double> percentageErrors = new ArrayList<>();
        for (int index = validationStart; index < points.size(); index++) {
            int localStart = Math.max(trainingStart, index - request.trainingSize());
            double predicted = predict(points, localStart, index, index,
                    request.seasonLength());
            double error = points.get(index).value() - predicted;
            errors.add(error);
            if (points.get(index).value() != 0) {
                percentageErrors.add(Math.abs(error / points.get(index).value()));
            }
        }
        double rmse = Math.sqrt(errors.stream().mapToDouble(value -> value * value)
                .average().orElse(0));
        double mae = errors.stream().mapToDouble(Math::abs).average().orElse(0);
        Double mape = percentageErrors.isEmpty() ? null
                : percentageErrors.stream().mapToDouble(Double::doubleValue).average().orElse(0) * 100;
        double zValue = inverseNormal((1 + request.confidenceLevel()) / 2);
        double halfWidth = zValue * Math.max(rmse, residualScale(points, trainingStart,
                validationStart, request.seasonLength()));
        long intervalMillis = regularInterval(points);
        List<ForecastPoint> forecast = new ArrayList<>();
        int historyEnd = points.size();
        List<Double> seasonalValues = new ArrayList<>(
                points.stream().map(TimePoint::value).toList());
        for (int offset = 1; offset <= request.horizon(); offset++) {
            int targetIndex = historyEnd + offset - 1;
            double predicted;
            if (request.seasonLength() > 0
                    && targetIndex - request.seasonLength() >= 0) {
                predicted = seasonalValues.get(targetIndex - request.seasonLength());
            } else {
                predicted = predict(points, Math.max(0, historyEnd - request.trainingSize()),
                        historyEnd, targetIndex, 0);
            }
            seasonalValues.add(predicted);
            Instant timestamp = points.get(points.size() - 1).timestamp()
                    .plusMillis(intervalMillis * offset);
            forecast.add(new ForecastPoint(timestamp, predicted, predicted - halfWidth,
                    predicted + halfWidth, request.confidenceLevel()));
        }
        double coverage = errors.stream().filter(value -> Math.abs(value) <= halfWidth).count()
                / (double) errors.size();
        TrainingWindow window = window(1, points, trainingStart, validationStart - 1,
                validationStart, points.size() - 1);
        String model = request.seasonLength() > 0 ? "SEASONAL_NAIVE_WITH_TREND_FALLBACK"
                : "LINEAR_TREND";
        return new ForecastResult(request.metricVersionId(), model, window,
                new BacktestMetrics(errors.size(), mae, rmse, mape, coverage),
                List.copyOf(forecast));
    }

    private List<TrainingWindow> buildWindows(List<TimePoint> points, int trainingSize,
            int validationSize, int step) {
        List<TrainingWindow> output = new ArrayList<>();
        int sequence = 1;
        for (int validationStart = trainingSize;
                validationStart + validationSize <= points.size(); validationStart += step) {
            int trainingStart = validationStart - trainingSize;
            output.add(window(sequence++, points, trainingStart, validationStart - 1,
                    validationStart, validationStart + validationSize - 1));
        }
        return List.copyOf(output);
    }

    private TrainingWindow window(int sequence, List<TimePoint> points,
            int trainingStart, int trainingEnd, int validationStart, int validationEnd) {
        return new TrainingWindow(sequence, trainingStart, trainingEnd,
                validationStart, validationEnd, points.get(trainingStart).timestamp(),
                points.get(trainingEnd).timestamp(), points.get(validationStart).timestamp(),
                points.get(validationEnd).timestamp());
    }

    private double predict(List<TimePoint> points, int start, int end,
            int targetIndex, int seasonLength) {
        int seasonalIndex = targetIndex - seasonLength;
        if (seasonLength > 0 && seasonalIndex >= start && seasonalIndex < points.size()) {
            return points.get(seasonalIndex).value();
        }
        int size = end - start;
        if (size < 2) {
            return points.get(end - 1).value();
        }
        double meanX = (size - 1) / 2.0;
        double meanY = 0;
        for (int index = start; index < end; index++) {
            meanY += points.get(index).value();
        }
        meanY /= size;
        double numerator = 0;
        double denominator = 0;
        for (int index = 0; index < size; index++) {
            double centered = index - meanX;
            numerator += centered * (points.get(start + index).value() - meanY);
            denominator += centered * centered;
        }
        double slope = denominator == 0 ? 0 : numerator / denominator;
        return meanY + slope * ((targetIndex - start) - meanX);
    }

    private double residualScale(List<TimePoint> points, int start, int end, int seasonLength) {
        if (end - start < 3) {
            return 0;
        }
        List<Double> residuals = new ArrayList<>();
        for (int index = start + 2; index < end; index++) {
            residuals.add(points.get(index).value()
                    - predict(points, start, index, index, seasonLength));
        }
        double sum = residuals.stream().mapToDouble(value -> value * value).sum();
        return Math.sqrt(sum / residuals.size());
    }

    private Stats stats(List<TimePoint> points, int start, int end) {
        double mean = 0;
        for (int index = start; index < end; index++) {
            mean += points.get(index).value();
        }
        mean /= end - start;
        double variance = 0;
        for (int index = start; index < end; index++) {
            double delta = points.get(index).value() - mean;
            variance += delta * delta;
        }
        return new Stats(mean, Math.sqrt(variance / (end - start)));
    }

    private List<TimePoint> ordered(List<TimePoint> input) {
        if (input == null || input.isEmpty()) {
            throw new BadRequestException("时间序列不能为空");
        }
        List<TimePoint> points = input.stream()
                .sorted(Comparator.comparing(TimePoint::timestamp)).toList();
        for (int index = 0; index < points.size(); index++) {
            TimePoint point = points.get(index);
            if (!Double.isFinite(point.value())) {
                throw new BadRequestException("时间序列不能包含非有限数值");
            }
            if (index > 0 && !points.get(index - 1).timestamp().isBefore(point.timestamp())) {
                throw new BadRequestException("时间戳必须唯一");
            }
        }
        return points;
    }

    private long regularInterval(List<TimePoint> points) {
        if (points.size() < 2) {
            throw new BadRequestException("预测至少需要两个时间点");
        }
        List<Long> intervals = new ArrayList<>();
        for (int index = 1; index < points.size(); index++) {
            intervals.add(Duration.between(points.get(index - 1).timestamp(),
                    points.get(index).timestamp()).toMillis());
        }
        intervals.sort(Long::compareTo);
        long median = intervals.get(intervals.size() / 2);
        if (median <= 0) {
            throw new BadRequestException("时间间隔必须大于零");
        }
        return median;
    }

    private void validateThresholds(Double lower, Double upper) {
        if (lower != null && upper != null && lower >= upper) {
            throw new BadRequestException("业务下限必须小于业务上限");
        }
    }

    /** Acklam 近似，足以覆盖接口允许的 80%—99% 置信水平。 */
    private double inverseNormal(double probability) {
        double[] a = {-39.6968302866538, 220.946098424521, -275.928510446969,
            138.357751867269, -30.6647980661472, 2.50662827745924};
        double[] b = {-54.4760987982241, 161.585836858041, -155.698979859887,
            66.8013118877197, -13.2806815528857};
        double[] c = {-0.00778489400243029, -0.322396458041136, -2.40075827716184,
            -2.54973253934373, 4.37466414146497, 2.93816398269878};
        double[] d = {0.00778469570904146, 0.32246712907004,
            2.445134137143, 3.75440866190742};
        if (probability < 0.02425) {
            double q = Math.sqrt(-2 * Math.log(probability));
            return (((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q
                    + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        if (probability > 0.97575) {
            double q = Math.sqrt(-2 * Math.log(1 - probability));
            return -(((((c[0] * q + c[1]) * q + c[2]) * q + c[3]) * q + c[4]) * q
                    + c[5]) / ((((d[0] * q + d[1]) * q + d[2]) * q + d[3]) * q + 1);
        }
        double q = probability - 0.5;
        double r = q * q;
        double numerator = (((((a[0] * r + a[1]) * r + a[2]) * r + a[3]) * r
                + a[4]) * r + a[5]) * q;
        double denominator = (((((b[0] * r + b[1]) * r + b[2]) * r + b[3]) * r
                + b[4]) * r + 1);
        return numerator / denominator;
    }

    private record Stats(double mean, double standardDeviation) {
    }
}
