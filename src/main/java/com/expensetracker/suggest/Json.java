package com.expensetracker.suggest;

/**
 * The two tiny JSON operations the LLM client needs: escaping a string for a request
 * body and reading the reply text. Kept dependency-free on purpose; if the app ever
 * needs more JSON than this, replace it with Jackson rather than growing it.
 */
final class Json {

    private Json() {
    }

    /** Returns {@code value} as a quoted JSON string literal. */
    static String quote(String value) {
        StringBuilder sb = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"': sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\n': sb.append("\\n"); break;
                case '\r': sb.append("\\r"); break;
                case '\t': sb.append("\\t"); break;
                default:
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
            }
        }
        return sb.append('"').toString();
    }

    /**
     * Reads the string value of the first {@code "text"} field in a JSON document,
     * e.g. the reply in an Anthropic Messages API response. Returns null if absent.
     */
    static String firstTextField(String json) {
        // Find "text" used as a KEY (followed by ':'), not as a value like "type":"text"
        int start = -1;
        for (int key = json.indexOf("\"text\""); key >= 0; key = json.indexOf("\"text\"", key + 1)) {
            int i = skipWhitespace(json, key + 6);
            if (i < json.length() && json.charAt(i) == ':') {
                i = skipWhitespace(json, i + 1);
                if (i < json.length() && json.charAt(i) == '"') {
                    start = i;
                    break;
                }
            }
        }
        if (start < 0) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = start + 1; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(++i);
                switch (next) {
                    case 'n': sb.append('\n'); break;
                    case 'r': sb.append('\r'); break;
                    case 't': sb.append('\t'); break;
                    case 'b': sb.append('\b'); break;
                    case 'f': sb.append('\f'); break;
                    case 'u':
                        if (i + 4 >= json.length()) {
                            return null;
                        }
                        sb.append((char) Integer.parseInt(json.substring(i + 1, i + 5), 16));
                        i += 4;
                        break;
                    default: sb.append(next); // \" \\ \/
                }
            } else {
                sb.append(c);
            }
        }
        return null; // unterminated string
    }

    private static int skipWhitespace(String s, int i) {
        while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
            i++;
        }
        return i;
    }
}
