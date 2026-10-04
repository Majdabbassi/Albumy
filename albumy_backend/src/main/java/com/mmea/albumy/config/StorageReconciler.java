package com.mmea.albumy.config;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.PhotoRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

/**
 * For hosts whose disk is wiped on every restart (e.g. a free Render instance) while the
 * database survives: removes photo rows and event covers whose files no longer exist, so
 * the app never serves broken images. {@link DataInitializer} runs afterwards and
 * re-creates the demo photos. Enabled with {@code app.demo.reconcile-storage=true}.
 */
@Component
@Profile("!worker")
@ConditionalOnProperty(name = "app.demo.reconcile-storage", havingValue = "true")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class StorageReconciler implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(StorageReconciler.class);
    private static final int PAGE_SIZE = 200;

    private final PhotoRepository photoRepository;
    private final EventRepository eventRepository;
    private final TransactionTemplate tx;
    private final Path uploadDir;

    public StorageReconciler(PhotoRepository photoRepository, EventRepository eventRepository,
                             PlatformTransactionManager txManager,
                             @Value("${upload.dir:uploads}") String uploadDir) {
        this.photoRepository = photoRepository;
        this.eventRepository = eventRepository;
        this.tx = new TransactionTemplate(txManager);
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath();
    }

    @Override
    public void run(String... args) {
        int removedPhotos = 0;
        int clearedCovers = 0;
        for (Event event : eventRepository.findAll()) {
            long lastId = 0;
            List<Photo> batch;
            do {
                batch = photoRepository.findNextBatch(event, lastId, PageRequest.of(0, PAGE_SIZE));
                List<Photo> missing = batch.stream()
                        .filter(p -> !Files.exists(uploadDir.resolve(p.getFileName())))
                        .toList();
                if (!missing.isEmpty()) {
                    tx.executeWithoutResult(s -> photoRepository.deleteAll(missing));
                    removedPhotos += missing.size();
                }
                if (!batch.isEmpty()) {
                    lastId = batch.get(batch.size() - 1).getId();
                }
            } while (batch.size() == PAGE_SIZE);

            String cover = event.getCoverFileName();
            if (cover != null && !Files.exists(uploadDir.resolve(cover))) {
                event.setCoverFileName(null);
                eventRepository.save(event);
                clearedCovers++;
            }
        }
        if (removedPhotos > 0 || clearedCovers > 0) {
            log.info("Storage reconcile: removed {} photo row(s) and {} cover(s) whose files were gone",
                    removedPhotos, clearedCovers);
        }
    }
}
