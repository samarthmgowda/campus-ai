package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Team;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface TeamRepository extends JpaRepository<Team, Long> {

    List<Team> findByEventId(Long eventId);

    List<Team> findByStatusOrderByFormedAtDesc(String status);

    @Query("select t from Team t where t.status = ?1")
    List<Team> findActiveTeams(String status);
}