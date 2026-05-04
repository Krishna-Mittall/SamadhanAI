package com.samadhanai.samadhanai.User.Repository;

import com.samadhanai.samadhanai.User.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UserRepository extends JpaRepository<User, Long> {

    // Auth — findByEmail (JwtAuthFilter + UserService use karta hai)
    Optional<User> findByEmail(String email);

    // Check duplicate email during registration
    boolean existsByEmail(String email);

    // Admin — enabled/disabled users filter
    List<User> findByEnabled(boolean enabled);

    // Admin — all users sorted by newest first
    List<User> findAllByOrderByCreatedAtDesc();
}