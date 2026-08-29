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
            case "memo" -> new WorkflowDef(
                    "memo",
                    "记一笔",
                    "确认后会写入个人知识库，之后可以直接问我。",
                    List.of(
                            new WorkflowSlot("title", "text", "标题", true, List.of()),
                            new WorkflowSlot("content", "textarea", "内容", true, List.of())
                    ),
                    "memo_save"
            );
            case "todo" -> new WorkflowDef(
                    "todo",
                    "待办事项",
                    "确认后记一条待办，可在控制台「待办」里改状态。",
                    List.of(
                            new WorkflowSlot("title", "text", "做什么", true, List.of()),
                            new WorkflowSlot("due", "choice", "什么时候", true,
                                    List.of("今天", "本周", "以后")),
                            new WorkflowSlot("remark", "text", "备注", false, List.of())
                    ),
                    "ticket_create"
            );
            case "todo_query" -> new WorkflowDef(
                    "todo_query",
                    "查待办",
                    "输入待办编号即可看进度。",
                    List.of(new WorkflowSlot("ticketNo", "text", "待办编号", true, List.of())),
                    "ticket_query"
            );
            case "milk_tea" -> new WorkflowDef(
                    "milk_tea",
                    "工位奶茶局",
                    "演示流程：点选规格，工位号可手填，提交后下单。",
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
                    "核对链接和分组后再写入博客书签页。",
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
        if ("memo".equals(intent)) {
            String body = message.replaceFirst("^(帮我)?(记一下|记下|备忘|记一笔)[：:，,\\s]*", "").trim();
            if (!body.isBlank()) {
                found.put("content", body);
                String title = body.split("[\\n。！？]")[0];
                if (title.length() > 24) {
                    title = title.substring(0, 24);
                }
                found.put("title", title);
            }
        }
        if ("todo".equals(intent)) {
            String body = message.replaceFirst("^(帮我)?(记个待办|待办|提醒我|别忘了)[：:，,\\s]*", "").trim();
            if (!body.isBlank()) {
                found.put("title", body.length() > 40 ? body.substring(0, 40) : body);
            }
        }
        return found;
    }

    public static boolean mentionsSlotOption(String intent, String message) {
        return !extractSlots(intent, message).isEmpty();
    }
}
