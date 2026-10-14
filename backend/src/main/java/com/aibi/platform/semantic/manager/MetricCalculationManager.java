package com.aibi.platform.semantic.manager;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.semantic.dto.SemanticDtos.CalculationResult;
import com.aibi.platform.semantic.dto.SemanticDtos.OrderSample;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class MetricCalculationManager {

    /** 固定订单口径：取消订单不计入，已支付金额减已确认退款金额。 */
    public CalculationResult netSales(List<OrderSample> orders, String targetCurrency,
            Map<String, BigDecimal> exchangeRates, int scale) {
        Set<String> currencies = orders.stream().filter(order -> !"CANCELLED".equals(order.status()))
                .map(OrderSample::currency).collect(java.util.stream.Collectors.toSet());
        if (currencies.size() > 1 && (targetCurrency == null || targetCurrency.isBlank()
                || exchangeRates == null || exchangeRates.isEmpty())) {
            throw new BadRequestException("跨币种金额禁止直接合计，必须提供目标币种和汇率");
        }
        String resultCurrency = targetCurrency == null || targetCurrency.isBlank()
                ? currencies.stream().findFirst().orElse("") : targetCurrency;
        BigDecimal total = BigDecimal.ZERO;
        for (OrderSample order : orders) {
            if ("CANCELLED".equals(order.status())) {
                continue;
            }
            BigDecimal amount = order.paidAmount().subtract(order.refundedAmount());
            if (!order.currency().equals(resultCurrency)) {
                BigDecimal rate = exchangeRates == null ? null : exchangeRates.get(order.currency());
                if (rate == null || rate.compareTo(BigDecimal.ZERO) <= 0) {
                    throw new BadRequestException("缺少币种 " + order.currency() + " 的已批准汇率");
                }
                amount = amount.multiply(rate);
            }
            total = total.add(amount);
        }
        return new CalculationResult("DEFINED", total.setScale(scale, RoundingMode.HALF_UP).toPlainString(),
                resultCurrency);
    }

    public CalculationResult safeDivide(BigDecimal numerator, BigDecimal denominator, int scale) {
        if (denominator == null || denominator.compareTo(BigDecimal.ZERO) == 0) {
            return new CalculationResult("UNDEFINED", null, null);
        }
        BigDecimal value = numerator.divide(denominator, scale, RoundingMode.HALF_UP);
        return new CalculationResult("DEFINED", value.toPlainString(), null);
    }
}
