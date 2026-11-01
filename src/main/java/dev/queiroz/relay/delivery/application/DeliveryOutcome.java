package dev.queiroz.relay.delivery.application;

/**
 * The normalized result of a delivery attempt. This is deliberately shared by
 * attempt-history persistence and retry/finalization decisions so the two
 * records cannot disagree about an HTTP response.
 */
public enum DeliveryOutcome {
    SUCCEEDED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE;

    public static DeliveryOutcome forHttpStatus(int status) {
        if (status >= 200 && status < 300) {
            return SUCCEEDED;
        }
        if (status == 408 || status == 429 || status >= 500) {
            return RETRYABLE_FAILURE;
        }
        return PERMANENT_FAILURE;
    }
}
