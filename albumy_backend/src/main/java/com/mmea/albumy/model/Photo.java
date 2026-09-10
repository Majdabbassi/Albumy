package com.mmea.albumy.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "photos", indexes = {
        @Index(name = "idx_photos_event_upload", columnList = "event_id, uploaded_at, id"),
        @Index(name = "idx_photos_event_sha256", columnList = "event_id, sha256", unique = true),
        @Index(name = "idx_photos_event_status", columnList = "event_id, status, uploaded_at")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Photo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "guest_id", nullable = false)
    private Guest guest;

    @Column(nullable = false)
    private String fileName;

    @Column
    private String originalName;

    @Column
    private String mimeType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PhotoStatus status = PhotoStatus.PROCESSING;

    @Column
    private String sha256;

    @Column
    private Integer width;

    @Column
    private Integer height;

    @Column
    private Long size;

    @Column
    private Long duration;

    @Column
    private LocalDate captureDate;

    @Column
    private String fileNameThumb;

    @Column
    private String fileNameMed;

    @Column
    private String fileNameFull;

    @Column
    private String fileNameWeb;

    @Column
    private String fileNamePoster;

    @Column
    private Integer errorCount = 0;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime uploadedAt;
}