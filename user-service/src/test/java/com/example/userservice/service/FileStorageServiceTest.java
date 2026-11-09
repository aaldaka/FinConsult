package com.example.userservice.service;

import com.example.userservice.exception.InvalidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and no database: files go into a temp directory
class FileStorageServiceTest {

    @TempDir Path tempDir;

    private FileStorageService service;

    @BeforeEach
    void setUp() {
        service = new FileStorageService();
        ReflectionTestUtils.setField(service, "uploadDir", tempDir.toString());
    }

    @Test
    void storesTheFileUnderTheSubfolderWithARandomName() throws Exception {
        byte[] bytes = {1, 2, 3, 4};
        MockMultipartFile file = new MockMultipartFile("file", "me.png", "image/png", bytes);

        String path = service.store(file, "profile_pictures");

        assertTrue(path.matches("/uploads/profile_pictures/[0-9a-f-]{36}\\.png"), "path was " + path);
        Path stored = tempDir.resolve("profile_pictures").resolve(path.substring(path.lastIndexOf('/') + 1));
        assertArrayEquals(bytes, Files.readAllBytes(stored));
    }

    @Test
    void theExtensionComesFromTheContentTypeNotFromTheClientsFilename() {
        MockMultipartFile file = new MockMultipartFile("file", "../../evil.exe", "image/jpeg", new byte[]{1});

        String path = service.store(file, "profile_pictures");

        assertTrue(path.endsWith(".jpg"), "path was " + path);
        assertTrue(!path.contains("evil") && !path.contains(".."));
    }

    @Test
    void anUnsupportedTypeIsRefused() {
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", new byte[]{1});

        InvalidRequest error = assertThrows(InvalidRequest.class, () -> service.store(file, "profile_pictures"));

        assertEquals("Unsupported file type", error.getMessage());
    }

    @Test
    void anEmptyOrMissingFileIsRefused() {
        MockMultipartFile empty = new MockMultipartFile("file", "me.png", "image/png", new byte[0]);

        assertEquals("No file was uploaded", assertThrows(InvalidRequest.class, () -> service.store(empty, "profile_pictures")).getMessage());
        assertEquals("No file was uploaded", assertThrows(InvalidRequest.class, () -> service.store(null, "profile_pictures")).getMessage());
    }
}
