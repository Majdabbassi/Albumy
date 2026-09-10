package com.mmea.albumy.util;

import com.mmea.albumy.model.Photo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

/** Centralized on-disk cleanup for photos, their variants and event covers. */
public final class PhotoFiles {

    private static final Logger log = LoggerFactory.getLogger(PhotoFiles.class);

    private PhotoFiles() {
    }

    public static void deleteAfterCommit(Runnable deleteAction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    deleteAction.run();
                }
            });
        } else {
            deleteAction.run();
        }
    }

    public static void deleteFileIfExists(String uploadDir, String fileName) {
        if (fileName == null || fileName.isBlank()) {
            return;
        }
        try {
            Files.deleteIfExists(Paths.get(uploadDir, fileName));
        } catch (IOException e) {
            log.warn("Could not delete file {}", fileName, e);
        }
    }

    public static void deletePhotoFiles(String uploadDir, Photo photo) {
        deleteFileIfExists(uploadDir, photo.getFileName());
        deleteFileIfExists(uploadDir, photo.getFileNameThumb());
        deleteFileIfExists(uploadDir, photo.getFileNameMed());
        deleteFileIfExists(uploadDir, photo.getFileNameFull());
        deleteFileIfExists(uploadDir, photo.getFileNameWeb());
        deleteFileIfExists(uploadDir, photo.getFileNamePoster());
    }
}