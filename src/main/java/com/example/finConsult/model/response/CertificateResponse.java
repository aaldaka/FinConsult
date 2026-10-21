package com.example.finConsult.model.response;

import lombok.*;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CertificateResponse {
    private Long id;
    private String originalFileName;
    private long fileSize;
}
