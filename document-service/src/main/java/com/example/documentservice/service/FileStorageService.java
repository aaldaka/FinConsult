package com.example.documentservice.service;

import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.InvalidRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

// stores the uploaded KYC documents and certificates on local disk. The stored path keeps the original monolith's form,
// /uploads/<subfolder>/<file>, so the paths already in the database still load; on disk it lives under app.upload-dir.
// The extension comes from the content type, never from the client's file name, and loads are confined to the upload
// directory so a stored path cannot reach anything outside it
@Service
public class FileStorageService {

    private static final String STORED_PREFIX = "/uploads/";
    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    private final Path uploadDir;

    public FileStorageService(@Value("${app.upload-dir:uploads}") String uploadDir) {
        this.uploadDir = Paths.get(uploadDir).toAbsolutePath().normalize();
    }

    public String store(MultipartFile file, String subfolder) {
        if (file == null || file.isEmpty()) {
            throw new InvalidRequest("No file was uploaded");
        }
        String contentType = file.getContentType();
        String extension = contentType == null ? null : EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new InvalidRequest("Unsupported file type");
        }
        try {
            Path dir = uploadDir.resolve(subfolder);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + extension;
            file.transferTo(dir.resolve(filename));
            return STORED_PREFIX + subfolder + "/" + filename;
        } catch (IOException e) {
            throw new InvalidRequest("Failed to store the uploaded file");
        }
    }

    // resolves a stored path back to a file, refusing anything outside the upload directory
    public Resource load(String storedPath) {
        if (storedPath == null || storedPath.isEmpty()) {
            throw new InformationNotFound("File Path not found");
        }
        if (!storedPath.startsWith(STORED_PREFIX)) {
            throw new InformationNotFound("File not found");
        }
        Path file = uploadDir.resolve(storedPath.substring(STORED_PREFIX.length())).normalize();
        if (!file.startsWith(uploadDir)) {
            throw new InformationNotFound("File not found");
        }
        if (!Files.exists(file)) {
            throw new InformationNotFound("File not found");
        }
        return new FileSystemResource(file);
    }
}
