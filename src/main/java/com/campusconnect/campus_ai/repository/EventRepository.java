package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Event;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EventRepository extends JpaRepository<Event, Long> {

    List<Event> findByTypeIgnoreCase(String type);

    List<Event> findByEventDateGreaterThanEqual(LocalDate date);

    List<Event> findByTypeIgnoreCaseAndEventDateGreaterThanEqual(String type, LocalDate date);
}
