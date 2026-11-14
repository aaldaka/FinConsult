package com.example.documentservice.controller;

import com.example.documentservice.client.UserClient;
import com.example.documentservice.service.DocumentProcessor;
import com.example.documentservice.support.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verifyNoInteractions;

// MockMvc skips the servlet's upload size check, so this runs a real server with a tiny upload limit and sends a file
// over it. The answer must be the 413 with the usual error body, and nothing may reach the service
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"spring.servlet.multipart.max-file-size=1KB", "spring.servlet.multipart.max-request-size=2KB"})
@ActiveProfiles("test")
@TestPropertySource(locations = "classpath:test.properties")
class KycUploadLimitTest {

    @LocalServerPort int port;
    @Value("${jwt.secret}") String jwtSecret;

    @MockitoBean UserClient userClient;
    @MockitoBean DocumentProcessor documentProcessor;

    @Test
    void aFileOverTheUploadLimitIs413() {
        MultiValueMap<String, Object> form = new LinkedMultiValueMap<>();
        form.add("file", new ByteArrayResource(new byte[1536]) {
            @Override
            public String getFilename() {
                return "big.pdf";
            }
        });

        Map<?, ?> error = RestClient.create("http://localhost:" + port).post()
                .uri("/kyc?documentType=PASSPORT")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + TestTokens.valid(jwtSecret, 910_001L, "CLIENT"))
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(form)
                .exchange((request, response) -> {
                    assertEquals(HttpStatusCode.valueOf(413), response.getStatusCode());
                    return response.bodyTo(Map.class);
                });

        assertEquals("The file is too large", error.get("message"));
        assertEquals(413, error.get("status"));
        verifyNoInteractions(userClient, documentProcessor);
    }
}
