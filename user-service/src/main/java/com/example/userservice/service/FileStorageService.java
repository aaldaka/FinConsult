package com.example.userservice.service;

import com.example.userservice.exception.InvalidRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

/**
 * Low-level file storage on local disk under the upload directory (app.upload-dir, "uploads" by default).
 * Content type is mapped to a fixed extension rather than trusting the client's original filename.
 */
@Service
public class FileStorageService { //main filtere for uploads (the profile service filters the acceptable extensions)

    @Value("${app.upload-dir:uploads}")
    private String uploadDir;

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf", //word doc vvv
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    /** Stores a file under {@code <upload dir>/<subfolder>} with a random name, typed off the content type (not the client's filename). */
    public String store(MultipartFile file, String subfolder) {
        if (file == null || file.isEmpty()) { // no file or an empty file
            throw new InvalidRequest("No file was uploaded");
        }

        String contentType = file.getContentType(); // missing content type
        String extension = contentType == null ? null : EXTENSION_BY_CONTENT_TYPE.get(contentType);
        if (extension == null) {
            throw new InvalidRequest("Unsupported file type");
        }

        try {
            Path dir = Path.of(uploadDir, subfolder);
            Files.createDirectories(dir);
            String filename = UUID.randomUUID() + extension;
            Path target = dir.resolve(filename);
            file.transferTo(target.toAbsolutePath());
            return "/uploads/" + subfolder + "/" + filename;
        } catch (IOException e) {
            throw new InvalidRequest("Failed to store the uploaded file");
        }
    }
}
