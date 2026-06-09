package com.example.triageagent.investigation;

public interface InvestigationStep {
    String getName();
    void execute(InvestigationState state);

    /**
     * Extracts a JSON object from model output, handling markdown fences and prose wrappers.
     * Tries fenced blocks first, then falls back to finding the outermost { } in the text.
     */
    static String extractJson(String s) {
        if (s == null) return null;
        String trimmed = s.strip();

        // Strip markdown fences
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            if (firstNewline != -1) trimmed = trimmed.substring(firstNewline + 1).strip();
            if (trimmed.endsWith("```")) trimmed = trimmed.substring(0, trimmed.length() - 3).stripTrailing();
            if (trimmed.startsWith("{")) return trimmed;
        }

        if (trimmed.startsWith("{")) return trimmed;

        // Extract first balanced { } block from prose
        int start = trimmed.indexOf('{');
        if (start == -1) return trimmed;
        int depth = 0;
        for (int i = start; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            if (c == '{') depth++;
            else if (c == '}') { if (--depth == 0) return trimmed.substring(start, i + 1); }
        }
        return trimmed.substring(start);
    }
}