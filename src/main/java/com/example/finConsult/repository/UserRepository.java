package com.example.finConsult.repository;

import com.example.finConsult.model.User;
import com.example.finConsult.model.enums.UserRole;
import com.example.finConsult.model.enums.UserStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByEmail(String email);
    boolean existsByEmail(String email);
    List<User> findByRole(UserRole role);
    List<User> findByRoleAndUserStatusNotOrderByIdAsc(UserRole role, UserStatus status);
    // db controls the strike
    @Query(value = """
            UPDATE users
            SET no_show_strikes = no_show_strikes + 1,
                blacklisted = (blacklisted OR no_show_strikes + 1 >= 3)
            WHERE id = :id
            RETURNING no_show_strikes
            """, nativeQuery = true)
    int addStrike(@Param("id") Long id);

    @Query(value = """
            UPDATE users
            SET no_show_strikes = GREATEST(no_show_strikes - 1, 0),
                blacklisted = (GREATEST(no_show_strikes - 1, 0) >= 3)
            WHERE id = :id
            RETURNING no_show_strikes
            """, nativeQuery = true)
    int removeStrike(@Param("id") Long id);
}