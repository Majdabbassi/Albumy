package com.mmea.albumy.config;

import com.mmea.albumy.model.Event;
import com.mmea.albumy.model.Guest;
import com.mmea.albumy.model.Invite;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.model.PhotoStatus;
import com.mmea.albumy.model.User;
import com.mmea.albumy.repository.EventRepository;
import com.mmea.albumy.repository.GuestRepository;
import com.mmea.albumy.repository.InviteRepository;
import com.mmea.albumy.repository.PhotoRepository;
import com.mmea.albumy.repository.UserRepository;
import com.mmea.albumy.util.RandomUtil;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import javax.imageio.ImageIO;

@Component
@Profile("!worker")
public class DataInitializer implements CommandLineRunner {

    private static final String ADMIN_USERNAME = "demo_admin";
    private static final String ADMIN_PASSWORD = "demo_admin_password";
    private static final String ORGANIZER_USERNAME = "demo_organizer";
    private static final String ORGANIZER_PASSWORD = "demo_organizer_password";
    private static final String DEMO_EVENT_NAME = "Demo Wedding";

    private final UserRepository userRepository;
    private final InviteRepository inviteRepository;
    private final EventRepository eventRepository;
    private final GuestRepository guestRepository;
    private final PhotoRepository photoRepository;
    private final PasswordEncoder passwordEncoder;
    private final String uploadDir;

    public DataInitializer(UserRepository userRepository, InviteRepository inviteRepository,
                           EventRepository eventRepository, GuestRepository guestRepository,
                           PhotoRepository photoRepository, PasswordEncoder passwordEncoder,
                           @Value("${upload.dir:uploads}") String uploadDir) {
        this.userRepository = userRepository;
        this.inviteRepository = inviteRepository;
        this.eventRepository = eventRepository;
        this.guestRepository = guestRepository;
        this.photoRepository = photoRepository;
        this.passwordEncoder = passwordEncoder;
        this.uploadDir = uploadDir;
    }

    @Override
    public void run(String... args) {
        createDirectories();
        seedAdmin();
        seedPendingInvite();
        User organizer = seedOrganizer();
        seedDemoEvent(organizer);
        logCredentials();
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(uploadDir));
        } catch (IOException e) {
            throw new RuntimeException("Could not create upload directory", e);
        }
    }

    private void seedAdmin() {
        if (userRepository.existsByUsername(ADMIN_USERNAME)) {
            return;
        }
        User admin = new User();
        admin.setUsername(ADMIN_USERNAME);
        admin.setPasswordHash(passwordEncoder.encode(ADMIN_PASSWORD));
        admin.setDisplayName("Demo Admin");
        admin.setEmail("admin@albumy-demo.example");
        admin.setRole(User.Role.ADMIN);
        userRepository.save(admin);
    }

    private void seedPendingInvite() {
        if (inviteRepository.count() > 0) {
            return;
        }
        Invite invite = new Invite();
        invite.setToken(UUID.randomUUID().toString());
        invite.setUsed(false);
        invite.setCreatedAt(LocalDateTime.now());
        invite.setExpiresAt(LocalDateTime.now().plusDays(7));
        inviteRepository.save(invite);
    }

    private User seedOrganizer() {
        User organizer = userRepository.findByUsername(ORGANIZER_USERNAME).orElse(null);
        if (organizer != null) {
            return organizer;
        }
        User newOrganizer = new User();
        newOrganizer.setUsername(ORGANIZER_USERNAME);
        newOrganizer.setPasswordHash(passwordEncoder.encode(ORGANIZER_PASSWORD));
        newOrganizer.setDisplayName("Demo Organizer");
        newOrganizer.setEmail("organizer@albumy-demo.example");
        newOrganizer.setRole(User.Role.ORGANIZER);
        userRepository.save(newOrganizer);
        return newOrganizer;
    }

    private void seedDemoEvent(User organizer) {
        Optional<Event> existing = eventRepository.findByName(DEMO_EVENT_NAME);
        Event event = existing.orElseGet(() -> eventRepository.save(newEvent(organizer)));
        if (photoRepository.countByEvent(event) > 0) {
            return;
        }

        List<String[]> uploads = List.of(
                new String[]{"Amelie", "3"},
                new String[]{"Karim", "2"},
                new String[]{"Sam", "2"}
        );

        int photoIndex = 0;
        for (String[] upload : uploads) {
            String guestName = upload[0];
            int count = Integer.parseInt(upload[1]);

            Guest guest = new Guest();
            guest.setEvent(event);
            guest.setName(guestName);
            guestRepository.save(guest);

            for (int i = 0; i < count; i++) {
                String fileName = UUID.randomUUID() + ".png";
                long fileSize = generatePlaceholderImage(fileName, photoIndex);

                Photo photo = new Photo();
                photo.setEvent(event);
                photo.setGuest(guest);
                photo.setFileName(fileName);
                photo.setOriginalName(fileName);
                photo.setMimeType("image/png");
                photo.setSize(fileSize);
                photo.setFileNameThumb(fileName);
                photo.setFileNameMed(fileName);
                photo.setFileNameFull(fileName);
                photo.setWidth(640);
                photo.setHeight(640);
                photo.setStatus(PhotoStatus.READY);
                photoRepository.save(photo);
                photoIndex++;
            }
        }
    }

    private Event newEvent(User organizer) {
        Event event = new Event();
        event.setName(DEMO_EVENT_NAME);
        event.setDate(LocalDate.now().minusDays(30));
        event.setStartTime(LocalTime.of(16, 0));
        event.setOrganizer(organizer);

        String eventCode;
        do {
            eventCode = RandomUtil.generateRandomCode(6);
        } while (eventRepository.existsByEventCode(eventCode));
        event.setEventCode(eventCode);

        String fullAlbumToken;
        do {
            fullAlbumToken = UUID.randomUUID().toString();
        } while (eventRepository.existsByFullAlbumToken(fullAlbumToken));
        event.setFullAlbumToken(fullAlbumToken);

        return event;
    }

    private long generatePlaceholderImage(String fileName, int index) {
        int size = 640;
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Graphics2D g2d = image.createGraphics();

        Color start = new Color(80 + (index * 47) % 160, 110 + (index * 23) % 120, 150 + (index * 31) % 90);
        Color end = new Color(210 + (index * 19) % 40, 150 + (index * 37) % 90, (index * 41) % 150);

        g2d.setPaint(new GradientPaint(0, 0, start, size, size, end));
        g2d.fillRect(0, 0, size, size);

        g2d.setFont(new Font("SansSerif", Font.BOLD, 42));
        g2d.setColor(Color.WHITE);
        g2d.drawString("Demo photo " + (index + 1), 60, size / 2);
        g2d.dispose();

        try {
            Path target = Paths.get(uploadDir, fileName);
            ImageIO.write(image, "png", target.toFile());
            return Files.size(target);
        } catch (IOException e) {
            throw new RuntimeException("Failed to seed demo photo", e);
        }
    }

    private void logCredentials() {
        System.out.println("=============================================");
        System.out.println("Demo accounts (fictional, demo only):");
        System.out.println("  Admin:     username=" + ADMIN_USERNAME + " password=" + ADMIN_PASSWORD);
        System.out.println("  Organizer: username=" + ORGANIZER_USERNAME + " password=" + ORGANIZER_PASSWORD);
        System.out.println("A pending invite link is available in the admin dashboard.");
        System.out.println("=============================================");
    }
}