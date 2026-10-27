package com.example.finConsult.model.response;

import com.example.finConsult.model.enums.CertificateStatus;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
@NoArgsConstructor
public class CertificateResponse {
    private Long id;
    private String originalFileName;
    private long fileSize;
    private CertificateStatus status;
    private String detectedType;
}
