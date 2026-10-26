package com.example.finConsult.service;

import org.springframework.core.io.FileSystemResource;
import com.example.finConsult.exception.InformationNotFound;
import com.example.finConsult.exception.InvalidRequest;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.UUID;

/**
 * Low-level file storage on local disk under the {@code uploads} directory.
 * Content type is mapped to a fixed extension rather than trusting the client's
 * original filename, and loads are confined to the upload directory to prevent
 * path traversal.
 */
@Service
public class FileStorageService { //main filtere for uploads (Niche profile and kyc doc will filter the acceptable extensions)

    private final String uploadDir = "uploads";

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf", //word doc vvv
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

    /** Stores a file under {@code uploads/<subfolder>} with a random name, typed off the content type (not the client's filename). */
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
            file.transferTo(target);

            return "/uploads/" + subfolder + "/" + filename;
        } catch (IOException e) {
            throw new InvalidRequest("Failed to store the uploaded file");
        }
    }

    /** Resolves a stored path back to a file, rejecting anything outside the upload directory. */
    public Resource load(String storedPath) {
        if (storedPath == null || storedPath.isEmpty()) {
            throw new InformationNotFound("File Path not found");
        }

        Path base = Paths.get(uploadDir).toAbsolutePath().normalize();
        Path file = Paths.get(storedPath.substring(1)).toAbsolutePath().normalize(); //remove /

        if (!file.startsWith(base)) {
            throw new InformationNotFound("File not found");
        }
        if (!Files.exists(file)) {
            throw new InformationNotFound("File not found");
        }

        return new FileSystemResource(file);
    }
}