package com.mmea.albumy.repository;

import com.mmea.albumy.model.Invite;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface InviteRepository extends JpaRepository<Invite, Long> {
    Optional<Invite> findByToken(String token);
    List<Invite> findAllByOrderByCreatedAtDesc();
    Page<Invite> findAllByOrderByCreatedAtDesc(Pageable pageable);

    /** Atomically burns an invite; only one concurrent caller can win (row lock). */
    @Modifying
    @Query("update Invite i set i.used = true where i.token = :token and i.used = false and i.expiresAt > :now")
    int markUsedIfUnused(@Param("token") String token, @Param("now") LocalDateTime now);
}