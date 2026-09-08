package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {
    List<Event> findByOrganizer(User organizer);
    Optional<Event> findByEventCode(String eventCode);
    Optional<Event> findByFullAlbumToken(String fullAlbumToken);
    Optional<Event> findByName(String name);
    boolean existsByEventCode(String eventCode);
    boolean existsByFullAlbumToken(String fullAlbumToken);
}
