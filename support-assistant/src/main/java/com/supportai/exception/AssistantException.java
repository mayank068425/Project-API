package com.supportai.exception;

/**
 * The assistant could not, or would not, produce an answer.
 *
 * <p>The {@link Kind} matters more than the message here, because these are not
 * all failures. {@code INSUFFICIENT_CONTEXT} and {@code OUTPUT_REJECTED} are the
 * system working correctly: it was asked for something it could not ground, and
 * it declined rather than inventing one.
 *
 * <p>That distinction has to survive to the API, or a client will retry a refusal
 * it should be showing to the user.
 */
public class AssistantException extends RuntimeException {

    public static final String CODE = "ASSISTANT_UNAVAILABLE";

    public enum Kind {
        /** The model provider is down, disabled, or timed out. Retryable. */
        PROVIDER_UNAVAILABLE,
        /** Not enough evidence to answer. Refusing is the correct outcome. */
        INSUFFICIENT_CONTEXT,
        /** An answer was produced and failed verification. Withheld, not shown. */
        OUTPUT_REJECTED
    }

    private final Kind kind;

    public AssistantException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    public AssistantException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }

    public Kind getKind() {
        return kind;
    }

    /** Whether a caller should try again, or accept the answer as final. */
    public boolean isRetryable() {
        return kind == Kind.PROVIDER_UNAVAILABLE;
    }
}
