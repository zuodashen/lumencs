package com.lumencs.knowledge;

import java.util.ArrayList;
import java.util.List;

public final class TextChunker {

    private TextChunker() {}

    public record Piece(String retrieval, String context) {
    }

    public record Options(boolean collapseWhitespace, boolean paragraph, int parentMax, int childMax, int overlap) {
        public static Options defaults() {
            return new Options(true, true, 500, 200, 40);
        }
    }

    public static List<String> chunk(String text, int size, int overlap) {
        return sliding(normalize(text, false), size, overlap);
    }

    /** 段落父块作上下文、较短子块作检索，接近 Dify 父子分段，但不拆两张表。 */
    public static List<Piece> split(String text, Options options) {
        Options opt = options == null ? Options.defaults() : options;
        String normalized = normalize(text, opt.collapseWhitespace());
        if (normalized.isBlank()) {
            return List.of();
        }
        List<String> parents = opt.paragraph()
                ? paragraphParents(normalized, Math.max(80, opt.parentMax()))
                : sliding(normalized, Math.max(80, opt.parentMax()), Math.max(0, opt.overlap()));
        int childMax = Math.max(40, opt.childMax());
        int overlap = Math.max(0, opt.overlap());
        List<Piece> pieces = new ArrayList<>();
        for (String parent : parents) {
            if (parent.length() <= childMax) {
                pieces.add(new Piece(parent, parent));
                continue;
            }
            for (String child : sliding(parent, childMax, overlap)) {
                pieces.add(new Piece(child, parent));
            }
        }
        return pieces;
    }

    public static List<String> preview(String text, Options options) {
        return split(text, options).stream().map(Piece::retrieval).toList();
    }

    private static String normalize(String text, boolean collapse) {
        if (text == null) {
            return "";
        }
        String normalized = text.replace("\r\n", "\n").trim();
        if (!collapse) {
            return normalized;
        }
        return normalized.replaceAll("[\\t ]+", " ").replaceAll("\\n{3,}", "\n\n").trim();
    }

    private static List<String> paragraphParents(String text, int parentMax) {
        String[] raw = text.split("\\n\\n+");
        List<String> paragraphs = new ArrayList<>();
        for (String part : raw) {
            String p = part.trim();
            if (!p.isBlank()) {
                paragraphs.add(p);
            }
        }
        if (paragraphs.isEmpty()) {
            return sliding(text, parentMax, 40);
        }
        List<String> parents = new ArrayList<>();
        StringBuilder bucket = new StringBuilder();
        for (String p : paragraphs) {
            if (p.length() > parentMax) {
                if (!bucket.isEmpty()) {
                    parents.add(bucket.toString().trim());
                    bucket.setLength(0);
                }
                parents.addAll(sliding(p, parentMax, 40));
                continue;
            }
            int extra = bucket.isEmpty() ? p.length() : bucket.length() + 2 + p.length();
            if (!bucket.isEmpty() && extra > parentMax) {
                parents.add(bucket.toString().trim());
                bucket.setLength(0);
            }
            if (!bucket.isEmpty()) {
                bucket.append("\n\n");
            }
            bucket.append(p);
        }
        if (!bucket.isEmpty()) {
            parents.add(bucket.toString().trim());
        }
        return parents;
    }

    private static List<String> sliding(String text, int size, int overlap) {
        if (text == null || text.isBlank()) {
            return List.of();
        }
        String normalized = text.trim();
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
