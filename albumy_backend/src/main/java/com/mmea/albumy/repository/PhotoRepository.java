package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PhotoRepository extends JpaRepository<Photo, Long> {
    List<Photo> findByEvent(Event event);
    long countByEvent(Event event);
    Optional<Photo> findFirstByEventAndSha256(Event event, String sha256);
    Optional<Photo> findFirstByEventAndStatusOrderByUploadedAtDesc(Event event, PhotoStatus status);

    // Always-paginated cursors (applied on the first page too).
    Page<Photo> findByEventOrderByUploadedAtDescIdDesc(Event event, Pageable pageable);
    Page<Photo> findByEventAndIdLessThanOrderByUploadedAtDescIdDesc(Event event, long beforeId, Pageable pageable);
    Page<Photo> findByGuestOrderByUploadedAtDescIdDesc(Guest guest, Pageable pageable);
    Page<Photo> findByGuestAndIdLessThanOrderByUploadedAtDescIdDesc(Guest guest, long beforeId, Pageable pageable);

    // Stuck-processing recovery sweep for the media worker.
    List<Photo> findByStatusAndUploadedAtBefore(PhotoStatus status, LocalDateTime uploadedAtBefore);

    // Batched id-cursor fetch used by event deletion so huge events never load all
    // photos into memory at once.
    @Query("select p from Photo p where p.event = :event and p.id > :lastId order by p.id asc")
    List<Photo> findNextBatch(@Param("event") Event event, @Param("lastId") long lastId, Pageable pageable);

    // Guest is eager-initialized so ZIP generation avoids an N+1.
    @Query("select p from Photo p join fetch p.guest where p.event = :event order by p.uploadedAt desc, p.id desc")
    List<Photo> findWithGuestsByEvent(@Param("event") Event event);

    // Batch cover/count helpers for list endpoints: one query totals both, instead of
    // running 2 per-event queries (2N) inside getEvents().
    @Query("""
            select p.event.id, p.fileNameThumb
            from Photo p
            where p.status = :status
              and p.event in :events
              and not exists (
                  select 1 from Photo p2
                  where p2.event = p.event and p2.status = :status
                    and (p2.uploadedAt > p.uploadedAt or (p2.uploadedAt = p.uploadedAt and p2.id > p.id))
              )
            """)
    List<Object[]> findLatestReadyThumbByEvents(@Param("events") List<Event> events,
                                                @Param("status") PhotoStatus status);

    @Query("select p.event.id, count(p) from Photo p where p.event in :events group by p.event.id")
    List<Object[]> countByEvents(@Param("events") List<Event> events);
}