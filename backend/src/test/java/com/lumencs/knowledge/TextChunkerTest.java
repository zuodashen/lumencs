package com.lumencs.knowledge;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 文本切分器单元测试：短文本单块、长文本按 size/overlap 切分、空输入安全、无空洞覆盖。
 */
class TextChunkerTest {

    @Test
    void 空输入返回空列表() {
        assertTrue(TextChunker.chunk(null, 10, 2).isEmpty());
        assertTrue(TextChunker.chunk("   ", 10, 2).isEmpty());
    }

    @Test
    void 短文本单块() {
        assertEquals(List.of("hello"), TextChunker.chunk("hello", 10, 2));
    }

    @Test
    void 长文本按尺寸切分并重叠() {
        String text = "0123456789abcdefghijklmnopqrstuvwxyz"; // 36 字符
        List<String> chunks = TextChunker.chunk(text, 10, 2);
        // 0-10 / 8-18 / 16-26 / 24-34 / 32-36
        assertEquals(5, chunks.size());
        assertEquals("0123456789", chunks.get(0));
        assertTrue(chunks.get(1).startsWith("89"), "第二块应包含重叠的 89");
        assertEquals("wxyz", chunks.get(chunks.size() - 1), "最后一块应收尾");
    }

    @Test
    void 长文本切分无空洞且首尾正确() {
        String text = "A".repeat(100) + "B".repeat(50);
        List<String> chunks = TextChunker.chunk(text, 30, 5);
        assertTrue(chunks.size() > 1);
        for (String c : chunks) {
            assertFalse(c.isBlank(), "不允许出现空块");
        }
        assertEquals(text.substring(0, 30), chunks.get(0), "首块应为原文前缀");
        assertEquals(text.substring(text.length() - chunks.get(chunks.size() - 1).length()),
                chunks.get(chunks.size() - 1), "末块应为原文后缀");
    }

    @Test
    void 段落父块短于子块上限时检索与上下文相同() {
        String text = "第一段说明。\n\n第二段补充。";
        List<TextChunker.Piece> pieces = TextChunker.split(text, TextChunker.Options.defaults());
        assertFalse(pieces.isEmpty());
        assertEquals(pieces.get(0).retrieval(), pieces.get(0).context());
    }

    @Test
    void 预览块数量与切分一致() {
        String text = "A".repeat(80) + "\n\n" + "B".repeat(80);
        assertEquals(TextChunker.split(text, TextChunker.Options.defaults()).size(),
                TextChunker.preview(text, TextChunker.Options.defaults()).size());
    }
}
