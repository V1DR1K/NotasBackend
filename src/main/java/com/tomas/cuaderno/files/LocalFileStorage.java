package com.tomas.cuaderno.files;

import java.io.*;
import java.nio.file.*;
import java.security.*;
import java.util.HexFormat;
import java.util.UUID;
import org.springframework.core.io.*;
import org.springframework.stereotype.Component;

@Component
public class LocalFileStorage implements FileStorage {
    private final Path root;

    public LocalFileStorage(FileProperties properties) {
        root = Paths.get(properties.getRoot()).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot initialize file storage", e);
        }
    }

    public StoredFile store(UUID key, InputStream content) throws IOException {
        Path destination = safe(key);
        Files.createDirectories(root);
        MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }

        BufferedInputStream buffered = content instanceof BufferedInputStream existing ? existing : new BufferedInputStream(content);
        buffered.mark(8192);
        String detectedType = java.net.URLConnection.guessContentTypeFromStream(buffered);
        buffered.reset();

        long size = 0;
        try (InputStream input = new DigestInputStream(buffered, digest); OutputStream output = Files.newOutputStream(destination, StandardOpenOption.CREATE_NEW)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
                size += read;
            }
        } catch (IOException ex) {
            Files.deleteIfExists(destination);
            throw ex;
        }

        String type = detectedType == null ? Files.probeContentType(destination) : detectedType;
        return new StoredFile(size, HexFormat.of().formatHex(digest.digest()), type == null ? "application/octet-stream" : type);
    }

    public Resource load(UUID key) {
        return new FileSystemResource(safe(key));
    }

    @Override
    public void delete(UUID key) throws IOException {
        Files.deleteIfExists(safe(key));
    }

    private Path safe(UUID key) {
        Path path = root.resolve(key.toString()).normalize();
        if (!path.startsWith(root)) throw new IllegalArgumentException("Invalid storage key");
        return path;
    }
}
