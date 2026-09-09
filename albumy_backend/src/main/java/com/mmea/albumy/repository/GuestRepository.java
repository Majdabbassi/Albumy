package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    Optional<Guest> findByEventAndName(Event event, String name);
    Optional<Guest> findByEventAndGuestToken(Event event, String guestToken);
    boolean existsByEventAndName(Event event, String name);
    List<Guest> findByEvent(Event event);
}