package com.smartresume.planner.storage;

import com.smartresume.planner.common.config.AppProperties;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class LocalFileStorage implements FileStorage {

    private final Path root;

    public LocalFileStorage(AppProperties props) throws IOException {
        this.root = Path.of(props.storage().localRoot()).toAbsolutePath().normalize();
        Files.createDirectories(root);
        log.info("File storage root: {}", root);
    }

    @Override
    public String store(byte[] content, String extension) throws IOException {
        String ext = extension == null ? "" : extension.replaceAll("[^A-Za-z0-9]", "");
        LocalDate today = LocalDate.now();
        String key = "%d/%02d/%s%s".formatted(today.getYear(), today.getMonthValue(), UUID.randomUUID(),
                ext.isEmpty() ? "" : "." + ext.toLowerCase());
        Path target = resolve(key);
        Files.createDirectories(target.getParent());
        Files.write(target, content);
        return key;
    }

    @Override
    public InputStream open(String key) throws IOException {
        try {
            return Files.newInputStream(resolve(key));
        } catch (NoSuchFileException e) {
            throw new IOException("Stored file is missing: " + key, e);
        }
    }

    @Override
    public void delete(String key) {
        if (key == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(key));
        } catch (IOException e) {
            log.warn("Could not delete stored file {}: {}", key, e.getMessage());
        }
    }

    /** Resolves a key under the root and refuses anything that escapes it. */
    private Path resolve(String key) {
        Path p = root.resolve(key).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Illegal storage key");
        }
        return p;
    }
}
