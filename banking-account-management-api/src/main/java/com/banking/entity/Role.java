package com.banking.entity;

import com.banking.util.Constants;
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
 * <p>The stored name has no {@code ROLE_} prefix. Spring Security's
 * {@code hasRole("BANK_EMPLOYEE")} adds that prefix itself before comparing, so
 * storing it here produces {@code ROLE_ROLE_BANK_EMPLOYEE} and every
 * authorisation check fails in a way that looks exactly like the role was never
 * granted. The prefix is added in one place only: {@link #authority()}.
 */
@Entity
@Table(name = "roles")
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

    /** ADMIN, BANK_EMPLOYEE or CUSTOMER. No prefix. */
    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    public String authority() {
        return Constants.ROLE_PREFIX + name;
    }

    /** Whether this role acts on the bank's behalf rather than a customer's. */
    public boolean isStaff() {
        return Constants.ROLE_ADMIN.equals(name) || Constants.ROLE_BANK_EMPLOYEE.equals(name);
    }
}
