package com.lumencs.modules.workflow;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkflowCatalogTest {

    @Test
    void todoExtractsDueFromSpokenTime() {
        Map<String, Object> slots = WorkflowCatalog.extractSlots("todo", "提醒我今天交材料");
        assertEquals("今天交材料", slots.get("title"));
        assertEquals("今天", slots.get("due"));
    }

    @Test
    void milkTeaAskPromptListsMissingChoices() {
        WorkflowDef def = WorkflowCatalog.of("milk_tea");
        String prompt = WorkflowCatalog.askPrompt(def, List.of("drink", "desk"), Map.of("size", "大杯", "count", "1"));
        assertTrue(prompt.contains("还差"));
        assertTrue(prompt.contains("喝什么"));
        assertTrue(prompt.contains("工位"));
        assertTrue(prompt.contains("已经记下"));
        assertTrue(prompt.contains("大杯"));
    }

    @Test
    void missingRequiredSlots() {
        WorkflowDef def = WorkflowCatalog.of("todo");
        List<String> missing = WorkflowCatalog.missing(def, Map.of("title", "交材料"));
        assertEquals(List.of("due"), missing);
    }
}
