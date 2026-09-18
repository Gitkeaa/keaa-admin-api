package com.keaa.adminapi.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.QueryTimeoutException;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;

/**
 * Turns "the database is not there right now" into an honest HTTP 503 with a JSON body.
 *
 * Without this, a dropped MySQL connection (Railway restarting the database, a pool
 * connection killed by an idle timeout, a redeploy in progress) surfaced as a bare 500 with an
 * HTML error page. The admin console maps anything that is not a 401 to "Could not reach the
 * server", so people went looking for a backend that was in fact running fine. A 503 with an
 * error message is what the frontend shows verbatim, and it is also the status Railway's
 * proxy and any uptime monitor understand as "temporarily unavailable, retry".
 *
 * Deliberately narrow: only connection-level failures. Validation errors, 404s and
 * everything else keep Spring's own handling and status codes.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler({DataAccessResourceFailureException.class, CannotCreateTransactionException.class,
            QueryTimeoutException.class})
    public ResponseEntity<Map<String, String>> databaseUnavailable(Exception e) {
        log.error("Database unavailable: {}", e.getMessage());
        return ResponseEntity.status(503).body(Map.of(
                "error", "The database is temporarily unavailable. Please try again in a moment."));
    }
}
