package com.expensetracker.suggest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

class JsonTest {

    @Test
    void quote_escapesSpecialCharacters() {
        assertEquals("\"say \\\"hi\\\"\\nnow \\\\ \\u0001\"", Json.quote("say \"hi\"\nnow \\ \u0001"));
    }

    @Test
    void firstTextField_readsTheReplyFromARealisticResponse() {
        String response = "{\"id\":\"msg_1\",\"type\":\"message\",\"role\":\"assistant\","
                + "\"content\":[{\"type\":\"text\",\"text\":\"Transport\"}],"
                + "\"stop_reason\":\"end_turn\"}";
        assertEquals("Transport", Json.firstTextField(response));
    }

    @Test
    void firstTextField_ignoresTextUsedAsAValue() {
        // "type":"text" comes first; the reader must not treat that value as the key
        assertEquals("Food", Json.firstTextField("{\"type\" : \"text\" , \"text\" : \"Food\"}"));
    }

    @Test
    void firstTextField_unescapes() {
        assertEquals("a\"b\nc\u00e9", Json.firstTextField("{\"text\":\"a\\\"b\\nc\\u00e9\"}"));
    }

    @Test
    void firstTextField_returnsNullWhenMissingOrBroken() {
        assertNull(Json.firstTextField("{\"error\":{}}"));
        assertNull(Json.firstTextField("{\"text\":\"unterminated"));
    }
}
