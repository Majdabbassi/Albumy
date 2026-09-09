package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByEvent(Event event);
    List<Photo> findByEventOrderByUploadedAtDesc(Event event);
    List<Photo> findByGuest(Guest guest);
    List<Photo> findByGuestOrderByUploadedAtDesc(Guest guest);
    long countByEvent(Event event);
    Optional<Photo> findFirstByEventAndSha256(Event event, String sha256);
    Optional<Photo> findFirstByEventAndStatusOrderByUploadedAtDesc(Event event, PhotoStatus status);
    Page<Photo> findByEventAndIdLessThanOrderByUploadedAtDescIdDesc(Event event, long beforeId, Pageable pageable);
    Page<Photo> findByGuestAndIdLessThanOrderByUploadedAtDescIdDesc(Guest guest, long beforeId, Pageable pageable);
}