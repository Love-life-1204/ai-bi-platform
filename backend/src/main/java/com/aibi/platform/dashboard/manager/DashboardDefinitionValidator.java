package com.aibi.platform.dashboard.manager;

import com.aibi.platform.common.json.ResourceIds;
import com.aibi.platform.common.exception.BadRequestException;
import com.aibi.platform.dashboard.dto.DashboardDtos.WidgetInput;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Component;

@Component
public class DashboardDefinitionValidator {

    private static final int GRID_COLUMNS = 12;

    public void validate(JsonNode layout, JsonNode filters, List<WidgetInput> widgets) {
        if (!layout.isObject() || layout.path("columns").asInt(GRID_COLUMNS) != GRID_COLUMNS) {
            throw new BadRequestException("看板布局必须使用 12 列网格");
        }
        if (!filters.isArray()) {
            throw new BadRequestException("全局筛选定义必须是数组");
        }
        if (filters.size() > 20) {
            throw new BadRequestException("全局筛选定义不能超过 20 个");
        }
        Set<String> filterKeys = new HashSet<>();
        for (JsonNode filter : filters) {
            String key = filter.path("key").asText("");
            String label = filter.path("label").asText("");
            String type = filter.path("type").asText("");
            long fieldId = ResourceIds.valid(filter.path("fieldId"))
                    ? ResourceIds.parse(filter.path("fieldId")) : 0;
            if (!filter.isObject() || !key.matches("[A-Za-z][A-Za-z0-9_-]{0,63}")
                    || label.isBlank() || fieldId <= 0
                    || !Set.of("DATE_RANGE", "SELECT", "MULTI_SELECT").contains(type)) {
                throw new BadRequestException("全局筛选定义不合法");
            }
            if (!filterKeys.add(key)) {
                throw new BadRequestException("全局筛选键不能重复");
            }
        }
        Set<String> keys = new HashSet<>();
        for (WidgetInput widget : widgets) {
            if (!keys.add(widget.key())) {
                throw new BadRequestException("看板组件键不能重复");
            }
            JsonNode position = widget.position();
            int x = position.path("x").asInt(-1);
            int y = position.path("y").asInt(-1);
            int width = position.path("w").asInt(-1);
            int height = position.path("h").asInt(-1);
            if (!position.isObject() || x < 0 || y < 0 || width < 1 || height < 1
                    || x + width > GRID_COLUMNS) {
                throw new BadRequestException("组件位置必须是有效的 12 列网格坐标");
            }
        }
    }
}
