package com.gephub.kyc_service.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class StorageService {
    private final Path root;

    public StorageService(@Value("${gephub.storage.root}") String rootPath) {
        this.root = Paths.get(rootPath);
    }

    public Path ensureSessionDir(UUID sessionId) throws IOException {
        Path dir = root.resolve(sessionId.toString());
        Files.createDirectories(dir);
        return dir;
    }

    public SavedFile save(UUID sessionId, MultipartFile file, String filename) throws IOException {
        Path dir = ensureSessionDir(sessionId);
        Path target = dir.resolve(filename);
        Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        String checksum = sha256(target);
        long size = Files.size(target);
        String mime = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
        return new SavedFile(target.toString(), mime, checksum, size);
    }

    private String sha256(Path path) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = Files.readAllBytes(path);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public record SavedFile(String filePath, String mimeType, String checksum, long sizeBytes) {}
}


