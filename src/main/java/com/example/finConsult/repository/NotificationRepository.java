package com.example.finConsult.repository;

import com.example.finConsult.model.Notification;
import com.example.finConsult.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NotificationRepository extends JpaRepository<Notification, Long> {
    List<Notification> findByUserAndIsReadFalse(User user);
}