package com.banking.repository;

import com.banking.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads a user with roles and customer in one query.
     *
     * <p>Used by {@code CustomUserDetailsService} on every authenticated request,
     * outside any transaction the application opened. Roles are eager on the
     * entity for that reason; the customer link is fetched here explicitly so
     * building the principal touches nothing lazy.
     */
    @Query("select u from User u left join fetch u.roles left join fetch u.customer "
            + "where u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByCustomerId(Long customerId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
