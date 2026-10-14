package com.hms.entity;

/**
 * ABO and Rh blood groups.
 *
 * <p>An enum rather than a free-text column. Blood group is looked up in an
 * emergency and is the field where a typo has the worst consequences: "O+" and
 * "0+" are indistinguishable at a glance, and a string column accepts both.
 */
public enum BloodGroup {

    A_POSITIVE("A+"),
    A_NEGATIVE("A-"),
    B_POSITIVE("B+"),
    B_NEGATIVE("B-"),
    AB_POSITIVE("AB+"),
    AB_NEGATIVE("AB-"),
    O_POSITIVE("O+"),
    O_NEGATIVE("O-");

    private final String label;

    BloodGroup(String label) {
        this.label = label;
    }

    public String getLabel() {
        return label;
    }

    /** Accepts the clinical notation used on forms, e.g. "O+" or "ab-". */
    public static BloodGroup fromLabel(String value) {
        if (value == null) {
            return null;
        }
        String normalised = value.trim().toUpperCase().replace(" ", "");
        for (BloodGroup group : values()) {
            if (group.label.equals(normalised) || group.name().equals(normalised)) {
                return group;
            }
        }
        throw new IllegalArgumentException("Unknown blood group: " + value);
    }
}
