package com.mmea.albumy.serviceimpl;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.service.EventService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** Needs MySQL + Redis, so it only runs when DB_URL is set (CI provides both). */
@EnabledIfEnvironmentVariable(named = "DB_URL", matches = ".+")
@SpringBootTest(properties = "jwt.secret=test-only-secret-0123456789abcdef0123456789abcdef")
class EventServiceIntegrationTest {

    @Autowired EventService eventService;
    @Autowired UserRepository userRepository;
    @Autowired EventRepository eventRepository;
    @Autowired GuestRepository guestRepository;
    @Autowired PhotoRepository photoRepository;

    // Regression: deleting an event with photos failed with "references an unsaved transient
    // instance", because photos were removed with a bulk delete that left them managed.
    @Test
    void deletingAnEventRemovesItsPhotosGuestsAndTheEvent() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        User organizer = new User();
        organizer.setUsername("it_" + suffix);
        organizer.setPasswordHash("not-a-real-hash");
        organizer.setDisplayName("IT organizer");
        organizer.setRole(User.Role.ORGANIZER);
        organizer = userRepository.save(organizer);

        Event event = new Event();
        event.setName("Integration event");
        event.setDate(LocalDate.of(2026, 1, 1));
        event.setStartTime(LocalTime.of(12, 0));
        event.setEventCode(suffix.toUpperCase().substring(0, 6));
        event.setFullAlbumToken(UUID.randomUUID().toString());
        event.setOrganizer(organizer);
        event = eventRepository.save(event);

        Guest guest = new Guest();
        guest.setEvent(event);
        guest.setName("guest_" + suffix);
        guest = guestRepository.save(guest);

        for (int i = 0; i < 3; i++) {
            Photo photo = new Photo();
            photo.setEvent(event);
            photo.setGuest(guest);
            photo.setFileName(UUID.randomUUID() + ".png");
            photo.setStatus(PhotoStatus.READY);
            photoRepository.save(photo);
        }
        assertEquals(3, photoRepository.countByEvent(event));

        Long eventId = event.getId();
        eventService.deleteEvent(eventId,
                org.springframework.security.core.userdetails.User.withUsername(organizer.getUsername())
                        .password("x").roles("ORGANIZER").build());

        assertFalse(eventRepository.findById(eventId).isPresent());
        assertEquals(0, photoRepository.countByEvent(event));
        assertEquals(0, guestRepository.findByEvent(event).size());

        userRepository.delete(organizer);
    }
}
