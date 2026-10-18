package com.campusconnect.campus_ai.repository;

import com.campusconnect.campus_ai.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    Optional<Document> findByTitle(String title);
    List<Document> findByHiddenFalse();
    List<Document> findByHiddenTrue();
    List<Document> findByUploaderId(Long uploaderId);
    long countByHiddenFalse();
    long countByHiddenTrue();
}
