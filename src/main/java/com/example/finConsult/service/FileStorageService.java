package com.example.finConsult.service;

import com.example.finConsult.exception.InvalidRequest;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;

@Service
public class FileStorageService { //main filtere for uploads (Niche profile and kyc doc will filter the acceptable extensions)

    private final String uploadDir = "uploads";

    private static final Map<String, String> EXTENSION_BY_CONTENT_TYPE = Map.of(
            "image/jpeg", ".jpg",
            "image/png", ".png",
            "application/pdf", ".pdf", //word doc vvv
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document", ".docx"
    );

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
}