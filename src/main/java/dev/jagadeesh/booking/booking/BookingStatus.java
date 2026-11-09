package dev.jagadeesh.booking.booking;

public enum BookingStatus {
    /** slot taken for a few minutes while the customer confirms (or pays) */
    HELD,
    CONFIRMED,
    CANCELLED,
    /** hold ran out before confirmation; slot freed */
    EXPIRED,
    COMPLETED,
    NO_SHOW;

    public boolean holdsTime() {
        return this == HELD || this == CONFIRMED;
    }
}
