package com.mmea.albumy.config;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Needs MySQL + Redis, so it only runs when DB_URL is set (CI provides both). */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest(properties = {
        "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef",
        "app.demo.reconcile-storage=true",
        "upload.dir=${java.io.tmpdir}/albumy-reconcile-test"
})
class StorageReconcilerIntegrationTest {

    @Autowired StorageReconciler reconciler;
    @Autowired UserRepository userRepository;
    @Autowired EventRepository eventRepository;
    @Autowired GuestRepository guestRepository;
    @Autowired PhotoRepository photoRepository;
    @Value("${upload.dir}") String uploadDir;

    // On hosts with a disposable disk the database outlives the files. Rows pointing at
    // vanished files must go, otherwise the gallery is full of broken images.
    @Test
    void removesPhotosAndCoversWhoseFilesAreGone() throws Exception {
        Path dir = Paths.get(uploadDir);
        Files.createDirectories(dir);
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User organizer = new User();
        organizer.setUsername("rc_" + suffix);
        organizer.setPasswordHash("x");
        organizer.setDisplayName("Reconcile organizer");
        organizer.setRole(User.Role.ORGANIZER);
        organizer = userRepository.save(organizer);

        Event event = new Event();
        event.setName("Reconcile event");
        event.setDate(LocalDate.of(2026, 1, 1));
        event.setStartTime(LocalTime.of(12, 0));
        event.setEventCode(suffix.toUpperCase().substring(0, 6));
        event.setFullAlbumToken(UUID.randomUUID().toString());
        event.setCoverFileName("gone-" + suffix + ".jpg");
        event.setOrganizer(organizer);
        event = eventRepository.save(event);

        Guest guest = new Guest();
        guest.setEvent(event);
        guest.setName("guest_" + suffix);
        guest = guestRepository.save(guest);

        String kept = "kept-" + suffix + ".png";
        Files.write(dir.resolve(kept), new byte[]{1, 2, 3});
        Photo keptPhoto = photo(event, guest, kept);
        Photo lostPhoto = photo(event, guest, "lost-" + suffix + ".png");

        reconciler.run();

        assertEquals(true, photoRepository.findById(keptPhoto.getId()).isPresent());
        assertEquals(false, photoRepository.findById(lostPhoto.getId()).isPresent());
        assertNull(eventRepository.findById(event.getId()).orElseThrow().getCoverFileName());

        photoRepository.delete(keptPhoto);
        guestRepository.delete(guest);
        eventRepository.delete(event);
        userRepository.delete(organizer);
        Files.deleteIfExists(dir.resolve(kept));
    }

    private Photo photo(Event event, Guest guest, String fileName) {
        Photo photo = new Photo();
        photo.setEvent(event);
        photo.setGuest(guest);
        photo.setFileName(fileName);
        photo.setStatus(PhotoStatus.READY);
        return photoRepository.save(photo);
    }
}
