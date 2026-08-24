package com.keaa.adminapi.chat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.time.Duration;
import java.util.List;

/** Talks to the Anthropic Messages API.
 *
 *  The knowledge base runs to tens of thousands of tokens and is byte-identical on every
 *  request, so it is sent as a CACHED prefix: the first call of a conversation writes the
 *  cache, every follow-up reads it back at about a tenth of the input price. Caching is a
 *  prefix match, so nothing volatile (no timestamps, no visitor ids) may go into that system
 *  block, or the cache is missed on every single request. */
@Service
@Slf4j
public class ClaudeService {

    private static final String API_URL = "https://api.anthropic.com/v1/messages";
    private static final String API_VERSION = "2023-06-01";

    /** A single reply's ceiling. The prompt asks for short answers, so this is a guard. */
    private static final int MAX_TOKENS = 2048;

    /** Covers 429 / 5xx / connection blips, mirroring the Node SDK's maxRetries: 3. */
    private static final int MAX_RETRIES = 3;

    private final ChatProperties props;
    private final KnowledgeBaseService knowledgeBase;
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestClient http;

    public ClaudeService(ChatProperties props, KnowledgeBaseService knowledgeBase) {
        this.props = props;
        this.knowledgeBase = knowledgeBase;

        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(10).toMillis());
        // A long answer over a large cached prompt can legitimately take a while.
        factory.setReadTimeout((int) Duration.ofSeconds(120).toMillis());

        this.http = RestClient.builder().baseUrl(API_URL).requestFactory(factory).build();
    }

    /** Prove the key works at boot, so a dead key shows up in the startup log rather than as a
     *  broken widget an hour later. Deliberately a bare ping with no system prompt: sending the
     *  knowledge base here would bill tens of thousands of tokens on every restart. */
    @PostConstruct
    void verifyApiKey() {
        String key = props.getApiKey();
        if (key == null || key.isBlank() || "your-api-key-here".equals(key)) {
            log.warn("[chat] ANTHROPIC_API_KEY is not set. The widget will answer offline, from site data only. "
                    + "Create a key at https://console.anthropic.com/settings/keys");
            return;
        }

        try {
            ObjectNode body = mapper.createObjectNode();
            body.put("model", props.getModel());
            body.put("max_tokens", 4);
            ObjectNode ping = body.putArray("messages").addObject();
            ping.put("role", "user");
            ping.put("content", "ping");

            post(body);
            log.info("[chat] Claude API key verified ({}), assistant is ready.", props.getModel());
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String hint = switch (status) {
                case 401, 403 -> "The key is invalid or revoked. Check ANTHROPIC_API_KEY.";
                case 400, 404 -> "The model \"" + props.getModel() + "\" was rejected. Check ANTHROPIC_MODEL.";
                case 429 -> "Rate limited, or the account is out of credit. Check your plan and usage.";
                default -> "Manage keys and credit at https://console.anthropic.com";
            };
            log.error("[chat] Claude REJECTED the request at startup (HTTP {}). The chat will fall back to "
                    + "offline answers. {} — {}", status, hint, e.getMessage());
        } catch (Exception e) {
            log.error("[chat] Could not reach the Claude API at startup: {}", e.getMessage());
        }
    }

    /** Ask Claude and return the reply text.
     *
     *  @param history  prior turns, already normalised to start with a user message
     *  @param message  the visitor's new message
     *  @param language ISO-639-1 code the site is being read in, or null for English */
    public String generateReply(List<ChatDtos.ChatRequest.HistoryMessage> history, String message, String language) {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", props.getModel());
        body.put("max_tokens", MAX_TOKENS);

        /*
         * The language instruction is a SECOND system block, after the cached one, never inside
         * it: caching is a prefix match, so the cached knowledge block stays byte-identical and
         * keeps hitting while this small uncached tail varies per visitor. English adds nothing,
         * the base prompt already answers in English.
         */
        ArrayNode system = body.putArray("system");
        ObjectNode cached = system.addObject();
        cached.put("type", "text");
        cached.put("text", knowledgeBase.getFullInstruction());
        cached.putObject("cache_control").put("type", "ephemeral");

        if (language != null && !"en".equals(language)) {
            ObjectNode langBlock = system.addObject();
            langBlock.put("type", "text");
            langBlock.put("text",
                    "The visitor is reading the site in the language with ISO code \"" + language + "\". "
                            + "Reply in that language. If they write to you in some other language, follow the "
                            + "language they actually write in. Keep product names, item codes and certification "
                            + "names in their original form.");
        }

        ArrayNode messages = body.putArray("messages");
        if (history != null) {
            for (ChatDtos.ChatRequest.HistoryMessage h : history) {
                ObjectNode m = messages.addObject();
                m.put("role", h.role());
                m.put("content", h.content() == null ? "" : h.content());
            }
        }
        ObjectNode userTurn = messages.addObject();
        userTurn.put("role", "user");
        userTurn.put("content", message);

        JsonNode response = post(body);

        // Claude 4+ can decline a request outright. There is no content to read when it does.
        if ("refusal".equals(response.path("stop_reason").asText())) {
            throw new IllegalStateException("The model declined to answer this request.");
        }

        StringBuilder text = new StringBuilder();
        for (JsonNode block : response.path("content")) {
            if ("text".equals(block.path("type").asText())) text.append(block.path("text").asText());
        }
        String reply = text.toString().trim();
        if (reply.isEmpty()) throw new IllegalStateException("The model returned an empty response.");

        // cache_read_input_tokens staying at 0 across requests means something volatile crept
        // into the system block and the cache is being paid for but never used.
        JsonNode usage = response.path("usage");
        log.info("[chat] replied via {} ({} new + {} cached input tokens)",
                response.path("model").asText(),
                usage.path("input_tokens").asInt(),
                usage.path("cache_read_input_tokens").asInt(0));

        return reply;
    }

    /** POST with retry on 429 and 5xx, exponential backoff, mirroring the Node SDK's behaviour. */
    private JsonNode post(ObjectNode body) {
        RuntimeException last = null;
        for (int attempt = 0; attempt <= MAX_RETRIES; attempt++) {
            try {
                return http.post()
                        .header("x-api-key", props.getApiKey())
                        .header("anthropic-version", API_VERSION)
                        .header("content-type", "application/json")
                        .body(body.toString())
                        .retrieve()
                        .body(JsonNode.class);
            } catch (RestClientResponseException e) {
                HttpStatusCode status = e.getStatusCode();
                boolean retryable = status.value() == 429 || status.is5xxServerError();
                if (!retryable || attempt == MAX_RETRIES) throw e;
                last = e;
                backOff(attempt);
            } catch (ResourceAccessException e) {
                if (attempt == MAX_RETRIES) throw e;
                last = e;
                backOff(attempt);
            }
        }
        throw last;
    }

    private static void backOff(int attempt) {
        try {
            Thread.sleep((long) Math.pow(2, attempt) * 500L);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while backing off before a Claude retry", ie);
        }
    }
}
