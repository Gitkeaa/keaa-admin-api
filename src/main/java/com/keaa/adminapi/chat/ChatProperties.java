package com.keaa.adminapi.chat;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** Chat settings, bound from application.properties.
 *
 *  The Anthropic key is read from the environment and never leaves this server: the browser
 *  only ever talks to our own /api/chat, never to Anthropic directly. */
@Component
@ConfigurationProperties(prefix = "app.chat")
@Getter
@Setter
public class ChatProperties {

    /** ANTHROPIC_API_KEY. Without it the widget still answers, but offline from site data only. */
    private String apiKey;

    /** Haiku 4.5 is the cheapest current Claude model and is ample for a knowledge-base bot. */
    private String model = "claude-haiku-4-5";
}
