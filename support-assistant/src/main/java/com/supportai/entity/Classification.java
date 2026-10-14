package com.supportai.entity;

/**
 * How sensitive a knowledge-base document is.
 *
 * <p>Ordered, so a clearance check is a comparison rather than a set membership
 * test — and so adding a level later does not require revisiting every call site.
 */
public enum Classification {

    PUBLIC(0),
    INTERNAL(1),
    /** Contains credentials, customer identifiers, or security procedure. */
    RESTRICTED(2);

    private final int level;

    Classification(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    public boolean isVisibleTo(Classification clearance) {
        return this.level <= clearance.level;
    }
}
