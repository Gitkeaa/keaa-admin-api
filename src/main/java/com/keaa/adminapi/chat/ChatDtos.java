package com.keaa.adminapi.chat;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** Request and response shapes for /api/chat. These mirror exactly what the website's chat
 *  widget (src/components/AiChat.jsx) already sends and reads, so the frontend needs no change. */
public final class ChatDtos {

    private ChatDtos() {}

    /** What the widget POSTs. Unknown fields are ignored so the frontend can add one safely. */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ChatRequest(String message, List<HistoryMessage> conversationHistory, String language) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record HistoryMessage(String role, String content) {}
    }

    /** What we return. The widget reads `message` on success and `error` on failure;
     *  `offline` marks an answer built from the local knowledge base rather than from Claude. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ChatResponse(String message, Boolean offline, String error, String details) {

        public static ChatResponse of(String message) {
            return new ChatResponse(message, null, null, null);
        }

        public static ChatResponse offline(String message) {
            return new ChatResponse(message, Boolean.TRUE, null, null);
        }

        public static ChatResponse error(String error, String details) {
            return new ChatResponse(null, null, error, details);
        }
    }
}
