package com.mmea.albumy.repository;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

public interface GuestRepository extends JpaRepository<Guest, Long> {
    Optional<Guest> findByEventAndName(Event event, String name);
    Optional<Guest> findByEventAndGuestToken(Event event, String guestToken);
    boolean existsByEventAndName(Event event, String name);
    List<Guest> findByEvent(Event event);

    @Modifying
    @Transactional
    @Query("update Guest g set g.guestToken = :token where g.id = :id and g.guestToken is null")
    int assignTokenIfNull(@Param("id") Long id, @Param("token") String token);
}