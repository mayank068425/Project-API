package com.example.lms.repository;

import com.example.lms.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * Loads a user with roles and member in one query.
     *
     * <p>Used by {@code CustomUserDetailsService}, which runs on every
     * authenticated request and outside any transaction the application opened.
     * Roles are eager on the entity for that reason; the member association is
     * fetched here explicitly so building the principal touches nothing lazy.
     */
    @Query("select u from User u left join fetch u.roles left join fetch u.member "
            + "where u.username = :username")
    Optional<User> findByUsernameWithRoles(@Param("username") String username);

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    Optional<User> findByMemberId(Long memberId);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}
