package dev.jagadeesh.booking.common;

/** The request is valid but the business says no (slot taken, too late to cancel...). Becomes 409 with a code. */
public class RuleViolation extends RuntimeException {
    private final String code;

    public RuleViolation(String code, String message) {
        super(message);
        this.code = code;
    }

    public String code() {
        return code;
    }
}
