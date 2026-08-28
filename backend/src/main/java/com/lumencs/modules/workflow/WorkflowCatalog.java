package com.lumencs.modules.workflow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

record WorkflowSlot(String name, String type, String label, boolean required, List<String> options) {
}

record WorkflowDef(
        String id,
        String title,
        String hint,
        List<WorkflowSlot> slots,
        String tool
) {
}

public final class WorkflowCatalog {

    private WorkflowCatalog() {}

    public static WorkflowDef of(String id) {
        return switch (id) {
            case "refund" -> new WorkflowDef(
                    "refund",
                    "退款申请",
                    "请点选原因并填写订单信息，提交后会创建工单。",
                    List.of(
                            new WorkflowSlot("orderId", "text", "订单号", true, List.of()),
                            new WorkflowSlot("reason", "choice", "退款原因", true,
                                    List.of("7天无理由", "质量问题", "发货延迟", "其他")),
                            new WorkflowSlot("remark", "text", "补充说明", false, List.of())
                    ),
                    "ticket_create"
            );
            case "account_open" -> new WorkflowDef(
                    "account_open",
                    "开户办理",
                    "选择产品并填写称呼，我们将生成开户工单。",
                    List.of(
                            new WorkflowSlot("product", "choice", "办理产品", true,
                                    List.of("普通证券账户", "基金账户", "理财产品A")),
                            new WorkflowSlot("displayName", "text", "称呼", true, List.of())
                    ),
                    "ticket_create"
            );
            case "ticket_query" -> new WorkflowDef(
                    "ticket_query",
                    "工单查询",
                    "输入工单号即可查询进度。",
                    List.of(new WorkflowSlot("ticketNo", "text", "工单号", true, List.of())),
                    "ticket_query"
            );
            case "complaint" -> new WorkflowDef(
                    "complaint",
                    "投诉建议",
                    "选择类型后提交，客服会跟进。",
                    List.of(
                            new WorkflowSlot("category", "choice", "投诉类型", true,
                                    List.of("服务态度", "处理时效", "产品说明", "其他")),
                            new WorkflowSlot("detail", "text", "具体情况", true, List.of())
                    ),
                    "ticket_create"
            );
            case "milk_tea" -> new WorkflowDef(
                    "milk_tea",
                    "工位奶茶局",
                    "改 bug 改到口渴了？点选规格，工位号可手填，提交后下单。",
                    List.of(
                            new WorkflowSlot("drink", "choice", "喝什么", true,
                                    List.of("生椰拿铁", "伯牙绝弦", "多肉葡萄", "美式")),
                            new WorkflowSlot("size", "choice", "杯型", true,
                                    List.of("中杯", "大杯")),
                            new WorkflowSlot("sweetness", "choice", "甜度", true,
                                    List.of("正常糖", "少糖", "半糖", "无糖")),
                            new WorkflowSlot("ice", "choice", "冰量", true,
                                    List.of("正常冰", "少冰", "去冰", "热")),
                            new WorkflowSlot("topping", "choice", "小料", true,
                                    List.of("珍珠", "椰果", "不加小料")),
                            new WorkflowSlot("count", "text", "杯数", true, List.of()),
                            new WorkflowSlot("desk", "text", "送到哪个工位", true, List.of())
                    ),
                    "tea_order"
            );
            case "blog_article" -> new WorkflowDef(
                    "blog_article",
                    "写博客草稿",
                    "我会先根据对话生成标题和正文，请你改完再确认。默认只存草稿，不会出现在前台。",
                    List.of(
                            new WorkflowSlot("title", "text", "标题", true, List.of()),
                            new WorkflowSlot("summary", "textarea", "摘要", false, List.of()),
                            new WorkflowSlot("content", "textarea", "正文（Markdown）", true, List.of()),
                            new WorkflowSlot("category", "text", "分类", true, List.of()),
                            new WorkflowSlot("tags", "text", "标签（逗号分隔）", false, List.of()),
                            new WorkflowSlot("action", "choice", "提交后", true,
                                    List.of("存草稿", "发布到前台"))
                    ),
                    "blog_article_upsert"
            );
            case "blog_bookmark" -> new WorkflowDef(
                    "blog_bookmark",
                    "添加书签",
                    "核对链接和分组后再写入博客书签页。书签没有文章标签，只有分组。",
                    List.of(
                            new WorkflowSlot("name", "text", "名称", true, List.of()),
                            new WorkflowSlot("link", "text", "链接", true, List.of()),
                            new WorkflowSlot("description", "textarea", "备注", false, List.of()),
                            new WorkflowSlot("category", "text", "书签分组", true, List.of())
                    ),
                    "blog_bookmark_create"
            );
            case "blog_tag" -> new WorkflowDef(
                    "blog_tag",
                    "新建文章标签",
                    "在博客里创建一个可用于文章的标签。",
                    List.of(new WorkflowSlot("name", "text", "标签名", true, List.of())),
                    "blog_tag_create"
            );
            default -> null;
        };
    }

    public static boolean isWorkflow(String intent) {
        return of(intent) != null;
    }

    public static List<String> missing(WorkflowDef def, Map<String, Object> slots) {
        List<String> miss = new ArrayList<>();
        Map<String, Object> safe = slots == null ? Map.of() : slots;
        for (WorkflowSlot slot : def.slots()) {
            if (!slot.required()) {
                continue;
            }
            Object v = safe.get(slot.name());
            if (v == null || v.toString().isBlank()) {
                miss.add(slot.name());
            }
        }
        return miss;
    }

    /** 从自然语言里抠选项，例如「大杯少糖珍珠」。 */
    public static Map<String, Object> extractSlots(String intent, String message) {
        Map<String, Object> found = new java.util.LinkedHashMap<>();
        WorkflowDef def = of(intent);
        if (def == null || message == null || message.isBlank()) {
            return found;
        }
        for (WorkflowSlot slot : def.slots()) {
            for (String option : slot.options()) {
                if (!option.isBlank() && message.contains(option)) {
                    found.put(slot.name(), option);
                    break;
                }
            }
        }
        if ("milk_tea".equals(intent)) {
            if (message.contains("两杯") || message.contains("2杯")) {
                found.put("count", "2");
            } else if (message.contains("一杯") || message.contains("1杯")) {
                found.putIfAbsent("count", "1");
            }
        }
        if ("blog_bookmark".equals(intent)) {
            java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("https?://\\S+").matcher(message);
            if (matcher.find()) {
                found.put("link", matcher.group().replaceAll("[)\\],，。]+$", ""));
            }
        }
        if ("blog_tag".equals(intent)) {
            String name = message.replaceAll(".*(标签|tag)", "").replaceAll("[：:]", "").trim();
            if (!name.isBlank() && name.length() <= 20) {
                found.put("name", name);
            }
        }
        return found;
    }

    public static boolean mentionsSlotOption(String intent, String message) {
        return !extractSlots(intent, message).isEmpty();
    }
}
