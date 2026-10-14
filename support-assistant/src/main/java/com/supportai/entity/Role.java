package com.supportai.entity;

import com.supportai.util.Constants;
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
 * <p>The stored name has no {@code ROLE_} prefix. {@code hasRole("LEAD")} adds it
 * before comparing, so storing it here gives {@code ROLE_ROLE_LEAD} and every
 * authorisation check fails in a way that looks exactly like the role was never
 * granted. The prefix is added in one place: {@link #authority()}.
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

    @Column(nullable = false, unique = true, length = 40)
    private String name;

    @Column(length = 200)
    private String description;

    /**
     * The highest document classification this role may retrieve.
     *
     * <p>Carried on the role rather than checked ad hoc, because retrieval is the
     * least obvious place a restricted runbook leaks and it must be impossible to
     * forget.
     */
    @Column(name = "max_classification", nullable = false, length = 20)
    @Builder.Default
    private String maxClassification = "INTERNAL";

    public String authority() {
        return Constants.ROLE_PREFIX + name;
    }

    public boolean isStaff() {
        return Constants.ROLE_ADMIN.equals(name) || Constants.ROLE_LEAD.equals(name);
    }
}
