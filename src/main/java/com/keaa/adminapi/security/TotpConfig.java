package com.keaa.adminapi.security;

import dev.samstevens.totp.code.*;
import dev.samstevens.totp.qr.QrGenerator;
import dev.samstevens.totp.qr.ZxingPngQrGenerator;
import dev.samstevens.totp.recovery.RecoveryCodeGenerator;
import dev.samstevens.totp.secret.DefaultSecretGenerator;
import dev.samstevens.totp.secret.SecretGenerator;
import dev.samstevens.totp.time.SystemTimeProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Ready-made TOTP helpers from the dev.samstevens.totp library — the whole 2FA feature. */
@Configuration
public class TotpConfig {

    @Bean
    SecretGenerator secretGenerator() {
        return new DefaultSecretGenerator();
    }

    @Bean
    QrGenerator qrGenerator() {
        return new ZxingPngQrGenerator();
    }

    @Bean
    CodeVerifier codeVerifier() {
        // Allow ±1 time step for clock drift (the library's default).
        return new DefaultCodeVerifier(new DefaultCodeGenerator(), new SystemTimeProvider());
    }

    @Bean
    RecoveryCodeGenerator recoveryCodeGenerator() {
        return new RecoveryCodeGenerator();
    }
}
