package com.lumencs.modules.skill;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillRegistryTest {

    private static SkillRegistry registry;

    @BeforeAll
    static void loadSkills() {
        registry = new SkillRegistry();
        registry.load();
    }

    @Test
    void loadsEveryBundledSkill() {
        assertEquals(16, registry.all().size());
        assertTrue(registry.intents().contains("blog_article"));
        assertTrue(registry.intents().contains("stock_quote"));
        assertTrue(registry.bodyFor("blog_article").contains("改稿"));
    }

    @Test
    void keywordRoutingMatchesHardSop() {
        assertEquals("memo", registry.matchIntent("帮我记一下明天带伞"));
        assertEquals("todo_query", registry.matchIntent("有哪些待办"));
        assertEquals("todo_update", registry.matchIntent("把 TK-20260101-AB 改成进行中"));
        assertEquals("todo", registry.matchIntent("提醒我周五交材料"));
        assertEquals("milk_tea", registry.matchIntent("点杯奶茶少糖"));
        assertEquals("blog_article", registry.matchIntent("帮我写一篇博客"));
        assertEquals("blog_list", registry.matchIntent("列出已发布博客"));
        assertEquals("blog_sync", registry.matchIntent("同步这篇博客"));
        assertEquals("stock_quote", registry.matchIntent("这只票可以买入吗"));
        assertEquals("stock_quote", registry.matchIntent("我现在浮亏4个点 补仓还是持有"));
        assertEquals("stock_quote", registry.matchIntent("查一下 600869 行情"));
        assertEquals("chitchat", registry.matchIntent("你好"));
        assertEquals("knowledge_rag", registry.matchIntent("向量数据库切块怎么做"));
    }

    @Test
    void cancelPhrasesStayOnSkill() {
        assertTrue(registry.cancelPhrases().contains("先不提交"));
        assertTrue(registry.cancelExclude().contains("润色"));
        assertFalse(registry.byIntent("workflow_cancel").orElseThrow().keyword());
    }
}
