package com.mmea.albumy.serviceimpl;

import com.drew.imaging.ImageMetadataReader;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mmea.albumy.model.Photo;
import com.mmea.albumy.service.MediaPipelineService;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class MediaPipelineServiceImpl implements MediaPipelineService {

    private final String uploadDir;
    private final List<Integer> imageSizes;
    private final String ffmpegPath;
    private final String ffprobePath;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public MediaPipelineServiceImpl(@Value("${upload.dir:uploads}") String uploadDir,
                                    @Value("${media.image.sizes:320,960,1920}") String imageSizes,
                                    @Value("${media.ffmpeg.path:ffmpeg}") String ffmpegPath,
                                    @Value("${media.ffprobe.path:ffprobe}") String ffprobePath) {
        this.uploadDir = uploadDir;
        this.ffmpegPath = ffmpegPath;
        this.ffprobePath = ffprobePath;
        this.imageSizes = new ArrayList<>();
        for (String s : imageSizes.split(",")) {
            int size = Integer.parseInt(s.trim());
            if (size > 0 && !this.imageSizes.contains(size)) {
                this.imageSizes.add(size);
            }
        }
        this.imageSizes.sort(Integer::compareTo);
    }

    @Override
    public void process(Photo photo) throws Exception {
        boolean video = photo.getMimeType() != null && photo.getMimeType().startsWith("video/");
        if (video) {
            processVideo(photo);
        } else {
            processImage(photo);
        }
    }

    private void processImage(Photo photo) throws Exception {
        Path original = Paths.get(uploadDir, photo.getFileName());
        if (!Files.exists(original)) {
            throw new IOException("Original file missing: " + original);
        }

        Metadata metadata = null;
        try {
            metadata = ImageMetadataReader.readMetadata(original.toFile());
        } catch (Exception ignored) {
        }
        if (metadata != null) {
            photo.setCaptureDate(readCaptureDate(metadata));
        }

        BufferedImage image = ImageIO.read(original.toFile());
        if (image == null) {
            image = decodeImageWithFfmpeg(original);
        }
        if (image == null) {
            throw new IOException("Unsupported or corrupt image format");
        }

        int orientation = metadata != null ? readOrientation(metadata) : 1;
        if (orientation > 1) {
            image = applyOrientation(image, orientation);
        }
        photo.setWidth(image.getWidth());
        photo.setHeight(image.getHeight());

        String base = baseName(photo.getFileName());
        int lastSize = -1;
        String lastVariant = null;
        String thumb = null;
        String med = null;
        String fullVariant = null;
        for (int size : imageSizes) {
            int effective = Math.min(size, image.getWidth());
            if (effective != lastSize) {
                BufferedImage resized = resizeKeepingAspect(image, effective);
                String suffix = labelFor(size);
                lastVariant = base + "_" + suffix + ".jpg";
                writeJpeg(resized, Paths.get(uploadDir, lastVariant), 0.82f);
                lastSize = effective;
            }
            switch (labelFor(size)) {
                case "thumb" -> thumb = lastVariant;
                case "med" -> med = lastVariant;
                default -> fullVariant = lastVariant;
            }
        }
        photo.setFileNameThumb(thumb);
        photo.setFileNameMed(med);
        photo.setFileNameFull(fullVariant);
    }

    private void processVideo(Photo photo) throws Exception {
        Path original = Paths.get(uploadDir, photo.getFileName());
        if (!Files.exists(original)) {
            throw new IOException("Original file missing: " + original);
        }

        JsonNode probe = probe(original);
        JsonNode stream = firstVideoStream(probe);
        if (stream != null) {
            photo.setWidth(stream.path("width").asInt(0));
            photo.setHeight(stream.path("height").asInt(0));
        }
        photo.setDuration((long) Math.ceil(probe.path("format").path("duration").asDouble(0.0)));

        boolean hasAudio = hasAudioStream(probe);
        String base = baseName(photo.getFileName());

        String web = base + "_web.mp4";
        transcodeToWeb(original, Paths.get(uploadDir, web), hasAudio);
        photo.setFileNameWeb(web);

        String poster = base + "_poster.jpg";
        extractPoster(original, Paths.get(uploadDir, poster));
        photo.setFileNamePoster(poster);
    }

    private JsonNode probe(Path file) throws IOException {
        Process process = new ProcessBuilder(
                ffprobePath, "-v", "quiet", "-print_format", "json",
                "-show_format", "-show_streams", file.toString()
        ).redirectErrorStream(true).start();
        String out;
        try (var in = process.getInputStream()) {
            out = new String(in.readAllBytes());
        }
        await(process, 120);
        return objectMapper.readTree(out);
    }

    private JsonNode firstVideoStream(JsonNode probe) {
        for (JsonNode s : probe.path("streams")) {
            if ("video".equals(s.path("codec_type").asText())) {
                return s;
            }
        }
        return null;
    }

    private boolean hasAudioStream(JsonNode probe) {
        for (JsonNode s : probe.path("streams")) {
            if ("audio".equals(s.path("codec_type").asText())) {
                return true;
            }
        }
        return false;
    }

    private void transcodeToWeb(Path in, Path out, boolean hasAudio) throws IOException {
        List<String> cmd = new ArrayList<>(List.of(
                ffmpegPath, "-y", "-i", in.toString(),
                "-c:v", "libx264", "-preset", "veryfast", "-crf", "23",
                "-profile:v", "main", "-pix_fmt", "yuv420p",
                "-movflags", "+faststart"
        ));
        if (hasAudio) {
            cmd.addAll(List.of("-c:a", "aac", "-b:a", "128k"));
        } else {
            cmd.addAll(List.of("-an"));
        }
        cmd.add(out.toString());
        run(cmd, 900);
    }

    private void extractPoster(Path in, Path out) throws IOException {
        List<String> cmd = List.of(
                ffmpegPath, "-y", "-ss", "1",
                "-i", in.toString(),
                "-frames:v", "1", "-vf", "scale='min(960,iw)':-2",
                out.toString()
        );
        run(cmd, 120);
    }

    private void run(List<String> cmd, long timeoutSeconds) throws IOException {
        ProcessBuilder pb = new ProcessBuilder(cmd).redirectErrorStream(true);
        Process process = pb.start();
        String out;
        try (var in = process.getInputStream()) {
            out = new String(in.readAllBytes());
        }
        if (!await(process, timeoutSeconds) || process.exitValue() != 0) {
            throw new IOException("Command failed: " + String.join(" ", cmd) + "\n" + out);
        }
    }

    private boolean await(Process process, long timeoutSeconds) {
        try {
            return process.waitFor(timeoutSeconds, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private LocalDate readCaptureDate(Metadata metadata) {
        try {
            ExifSubIFDDirectory sub = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            java.util.Date d = sub != null ? sub.getDateOriginal() : null;
            if (d != null) {
                return d.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDate();
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private int readOrientation(Metadata metadata) {
        try {
            ExifIFD0Directory ifd0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            return ifd0 != null ? ifd0.getInt(ExifIFD0Directory.TAG_ORIENTATION) : 1;
        } catch (Exception e) {
            return 1;
        }
    }

    private BufferedImage applyOrientation(BufferedImage src, int orientation) {
        int width = src.getWidth();
        int height = src.getHeight();
        boolean swapped = orientation >= 5 && orientation <= 8;
        int outW = swapped ? height : width;
        int outH = swapped ? width : height;

        BufferedImage out = new BufferedImage(outW, outH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        AffineTransform t = new AffineTransform();
        t.translate((outW - width) / 2.0, (outH - height) / 2.0);
        if (swapped) {
            t.translate((width - outW) / 2.0, (height - outH) / 2.0);
        }
        switch (orientation) {
            case 2 -> t.scale(-1.0, 1.0);
            case 3 -> t.rotate(Math.PI, width / 2.0, height / 2.0);
            case 4 -> { t.translate(width, 0); t.scale(-1.0, 1.0); }
            case 5 -> { t.rotate(Math.PI / 2, width / 2.0, height / 2.0); t.translate(width, 0); t.scale(-1.0, 1.0); }
            case 6 -> t.rotate(Math.PI / 2, width / 2.0, height / 2.0);
            case 7 -> { t.rotate(-Math.PI / 2, width / 2.0, height / 2.0); t.translate(width, 0); t.scale(-1.0, 1.0); }
            case 8 -> t.rotate(-Math.PI / 2, width / 2.0, height / 2.0);
            default -> {}
        }
        g.drawImage(src, t, null);
        g.dispose();
        return out;
    }

    private BufferedImage decodeImageWithFfmpeg(Path original) throws IOException {
        Path tmp = original.resolveSibling(original.getFileName() + ".decode.png");
        try {
            List<String> cmd = List.of(
                    ffmpegPath, "-y", "-i", original.toString(),
                    "-frames:v", "1", "-an",
                    tmp.toString()
            );
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            try (var in = process.getInputStream()) {
                in.readAllBytes();
            }
            if (!await(process, 120) || process.exitValue() != 0) {
                throw new IOException("ffmpeg could not decode image");
            }
            return ImageIO.read(tmp.toFile());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private BufferedImage resizeKeepingAspect(BufferedImage src, int maxWidth) {
        int newW = maxWidth;
        int newH = Math.max(1, (int) Math.round((double) src.getHeight() * newW / src.getWidth()));
        BufferedImage out = new BufferedImage(newW, newH, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(src, 0, 0, newW, newH, null);
        g.dispose();
        return out;
    }

    private void writeJpeg(BufferedImage image, Path target, float quality) throws IOException {
        ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
        try (ImageOutputStream ios = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(ios);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(quality);
            }
            writer.write(null, new IIOImage(image, null, null), param);
        } finally {
            writer.dispose();
        }
    }

    private String baseName(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    private String labelFor(int size) {
        if (size <= 400) {
            return "thumb";
        }
        if (size <= 1200) {
            return "med";
        }
        return "full";
    }
}