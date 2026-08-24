package com.keaa.adminapi.chat;

import com.keaa.adminapi.chat.ChatDtos.ChatRequest;
import com.keaa.adminapi.chat.ChatDtos.ChatResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** /api/chat — the public website's AI chat widget.
 *
 *  Public (no auth), like the other website-form endpoints. This replaced a separate Node
 *  service (the frontend repo's old server.js), so the request and response shapes match what
 *  the widget already sends and reads and the frontend needed no change. */
@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    /** Whitelist, not trust: the body is visitor input, so anything but a plain code is dropped. */
    private static final Pattern LANGUAGE_CODE = Pattern.compile("^[a-z]{2}$");

    private final ClaudeService claude;
    private final OfflineAnswerService offline;

    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        String message = request.message();
        if (message == null || message.isBlank()) {
            return ResponseEntity.badRequest().body(ChatResponse.error("Invalid message", null));
        }

        String raw = request.language();
        String language = raw != null && LANGUAGE_CODE.matcher(raw).matches() ? raw : null;

        try {
            return ResponseEntity.ok(ChatResponse.of(claude.generateReply(priorHistory(request), message, language)));
        } catch (Exception e) {
            logFailure(e);

            // The visitor should not pay for our outage: answer from the site's own data instead.
            String fallback = offline.answer(message);
            if (fallback != null) return ResponseEntity.ok(ChatResponse.offline(fallback));

            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ChatResponse.error(
                    "The assistant is temporarily unavailable. Please try again later.", e.getMessage()));
        }
    }

    /** Drop the just-sent user message (the last item the widget includes) and any leading
     *  assistant greeting, since a conversation has to start with a user turn. */
    private List<ChatRequest.HistoryMessage> priorHistory(ChatRequest request) {
        List<ChatRequest.HistoryMessage> incoming = request.conversationHistory();
        if (incoming == null || incoming.size() < 2) return List.of();

        List<ChatRequest.HistoryMessage> out = new ArrayList<>();
        for (int i = 0; i < incoming.size() - 1; i++) {
            ChatRequest.HistoryMessage src = incoming.get(i);
            out.add(new ChatRequest.HistoryMessage(
                    "user".equals(src.role()) ? "user" : "assistant",
                    src.content() == null ? "" : src.content()));
        }
        while (!out.isEmpty() && !"user".equals(out.getFirst().role())) out.removeFirst();
        return out;
    }

    /** Say why in the log, precisely, so a broken deploy is diagnosable at a glance. */
    private void logFailure(Exception e) {
        if (e instanceof RestClientResponseException apiError) {
            int status = apiError.getStatusCode().value();
            log.error("[chat] Claude API error (HTTP {}): {}", status, apiError.getMessage());
            if (status == 401 || status == 403) {
                log.error("[chat] Claude rejected the API key ({}). Check ANTHROPIC_API_KEY and that the account "
                        + "has credit. Manage keys at https://console.anthropic.com/settings/keys", status);
            } else if (status == 429) {
                log.error("[chat] rate limited by the Claude API even after retries.");
            }
        } else {
            log.error("[chat] Claude call failed: {}", e.getMessage());
        }
    }
}
