package com.lumencs.knowledge;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {

    private TextChunker() {}

    public static List<String> chunk(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.length() <= size) {
            return List.of(normalized);
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < normalized.length()) {
            int end = Math.min(normalized.length(), start + size);
            chunks.add(normalized.substring(start, end));
            if (end == normalized.length()) {
                break;
            }
            start = Math.max(end - overlap, start + 1);
        }
        return chunks;
    }
}
