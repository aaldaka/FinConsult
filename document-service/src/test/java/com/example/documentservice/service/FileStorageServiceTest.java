package com.example.documentservice.service;

import com.example.documentservice.exception.InformationNotFound;
import com.example.documentservice.exception.InvalidRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.core.io.Resource;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

// no Spring and no database, every file goes into a temp dir that JUnit deletes afterwards
class FileStorageServiceTest {

    private static final byte[] PDF = {0x25, 0x50, 0x44, 0x46, 0x2D, 0x31};

    @TempDir Path uploadDir;

    private FileStorageService storage;

    @BeforeEach
    void setUp() {
        storage = new FileStorageService(uploadDir.toString());
    }

    @Test
    void aFileIsStoredUnderARandomNameWithTheExtensionOfItsContentType() throws Exception {
        String stored = storage.store(new MockMultipartFile("file", "passport.exe", "application/pdf", PDF), "kyc_documents");

        assertTrue(stored.matches("/uploads/kyc_documents/[0-9a-f-]{36}\\.pdf"), stored);
        Path onDisk = uploadDir.resolve("kyc_documents").resolve(stored.substring(stored.lastIndexOf('/') + 1));
        assertArrayEquals(PDF, Files.readAllBytes(onDisk));
    }

    @Test
    void eachContentTypeGetsItsOwnExtensionAndTwoUploadsNeverShareAName() {
        assertTrue(storage.store(new MockMultipartFile("f", "a", "image/jpeg", PDF), "x").endsWith(".jpg"));
        assertTrue(storage.store(new MockMultipartFile("f", "a", "image/png", PDF), "x").endsWith(".png"));
        assertTrue(storage.store(new MockMultipartFile("f", "a",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", PDF), "x").endsWith(".docx"));
        assertNotEquals(storage.store(new MockMultipartFile("f", "a", "application/pdf", PDF), "x"),
                storage.store(new MockMultipartFile("f", "a", "application/pdf", PDF), "x"));
    }

    @Test
    void anEmptyFileOrAnUnsupportedTypeIsRefused() {
        assertThrows(InvalidRequest.class, () -> storage.store(null, "kyc_documents"));
        assertThrows(InvalidRequest.class, () -> storage.store(new MockMultipartFile("f", "a.pdf", "application/pdf", new byte[0]), "kyc_documents"));
        assertEquals("Unsupported file type", assertThrows(InvalidRequest.class,
                () -> storage.store(new MockMultipartFile("f", "a.gif", "image/gif", PDF), "kyc_documents")).getMessage());
        assertThrows(InvalidRequest.class, () -> storage.store(new MockMultipartFile("f", "a", null, PDF), "kyc_documents"));
    }

    @Test
    void aStoredFileLoadsBack() throws Exception {
        String stored = storage.store(new MockMultipartFile("file", "id.pdf", "application/pdf", PDF), "kyc_documents");

        Resource resource = storage.load(stored);

        assertArrayEquals(PDF, resource.getInputStream().readAllBytes());
    }

    @Test
    void aPathThatEscapesTheUploadDirIsRefused() throws Exception {
        Path outside = Files.writeString(uploadDir.getParent().resolve("outside-" + System.nanoTime() + ".txt"), "secret");
        try {
            for (String path : new String[]{"/uploads/../" + outside.getFileName(), "/uploads/kyc_documents/../../" + outside.getFileName(),
                    "/etc/passwd", "uploads/kyc_documents/a.pdf", outside.toString()}) {
                assertThrows(InformationNotFound.class, () -> storage.load(path), path);
            }
        } finally {
            Files.deleteIfExists(outside);
        }
    }

    @Test
    void aMissingOrEmptyPathIsNotFound() {
        assertThrows(InformationNotFound.class, () -> storage.load(null));
        assertThrows(InformationNotFound.class, () -> storage.load(""));
        assertThrows(InformationNotFound.class, () -> storage.load("/uploads/kyc_documents/missing.pdf"));
    }
}
