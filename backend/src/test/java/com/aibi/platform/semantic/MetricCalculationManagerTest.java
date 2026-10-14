package com.aibi.platform.semantic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.semantic.dto.SemanticDtos.CalculationResult;
import com.aibi.platform.semantic.dto.SemanticDtos.OrderSample;
import com.aibi.platform.semantic.manager.MetricCalculationManager;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class MetricCalculationManagerTest {

    private final MetricCalculationManager manager = new MetricCalculationManager();

    @Test
    void fixedOrderSampleProducesNetSalesOf250Yuan() {
        List<OrderSample> orders = List.of(
                order("PAID", "CNY", "100", "0"),
                order("PAID", "CNY", "200", "50"),
                order("CANCELLED", "CNY", "300", "0"));

        CalculationResult result = manager.netSales(orders, "CNY", Map.of(), 2);

        assertThat(result.status()).isEqualTo("DEFINED");
        assertThat(result.value()).isEqualTo("250.00");
        assertThat(result.currency()).isEqualTo("CNY");
    }

    @Test
    void crossCurrencyRequiresApprovedRatesAndConvertsBeforeAggregation() {
        List<OrderSample> orders = List.of(
                order("PAID", "CNY", "100", "0"),
                order("PAID", "USD", "10", "0"));

        assertThatThrownBy(() -> manager.netSales(orders, null, Map.of(), 2))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("跨币种");
        assertThat(manager.netSales(orders, "CNY", Map.of("USD", new BigDecimal("7")), 2).value())
                .isEqualTo("170.00");
    }

    @Test
    void zeroDenominatorIsUndefinedRatherThanZeroOrInfinity() {
        assertThat(manager.safeDivide(new BigDecimal("12"), BigDecimal.ZERO, 4))
                .isEqualTo(new CalculationResult("UNDEFINED", null, null));
    }

    private OrderSample order(String status, String currency, String paid, String refunded) {
        return new OrderSample(status, currency, new BigDecimal(paid), new BigDecimal(refunded));
    }
}
