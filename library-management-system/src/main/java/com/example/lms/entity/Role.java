package com.example.lms.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A granted role, as a table rather than an enum.
 *
 * <p>An enum is simpler and adequate while the set never changes. A table earns
 * its place the first time an administrator needs to add a role — an auditor, a
 * volunteer, a branch manager — without a rebuild and a redeploy.
 *
 * <p>The stored name has no {@code ROLE_} prefix. Spring Security's
 * {@code hasRole("LIBRARIAN")} adds that prefix itself before comparing, so
 * storing it here produces {@code ROLE_ROLE_LIBRARIAN} and every authorisation
 * check fails in a way that looks like the role was never granted. The prefix is
 * added in exactly one place, {@link #authority()}.
 */
@Entity
@Table(name = "role")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode(of = "name")
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "role_id")
    private Long id;

    /** ADMIN, LIBRARIAN or MEMBER. No prefix. */
    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    public String authority() {
        return com.example.lms.util.Constants.ROLE_PREFIX + name;
    }

    public boolean isStaff() {
        return com.example.lms.util.Constants.ROLE_ADMIN.equals(name)
                || com.example.lms.util.Constants.ROLE_LIBRARIAN.equals(name);
    }
}
