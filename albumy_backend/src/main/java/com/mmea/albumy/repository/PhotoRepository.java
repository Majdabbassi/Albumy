package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByEvent(Event event);
    List<Photo> findByEventAndUploaderName(Event event, String uploaderName);
    Optional<Photo> findFirstByEventAndUploaderName(Event event, String uploaderName);
    boolean existsByEventAndUploaderName(Event event, String uploaderName);
}
