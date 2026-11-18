package com.example.documentservice.controller;

import com.example.documentservice.model.enums.DocumentType;
import com.example.documentservice.model.response.KycApproval;
import com.example.documentservice.service.KycService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

// called by the booking service only, guarded by the internal key (see InternalKeyFilter)
@RestController
@RequestMapping("/internal/kyc")
@RequiredArgsConstructor
public class InternalKycController {

    private final KycService kycService;

    // without a documentType any approved document counts, for booking-service's check before a booking
    @GetMapping("/approved")
    public KycApproval approved(@RequestParam Long userId, @RequestParam(required = false) DocumentType documentType) {
        return new KycApproval(kycService.isApproved(userId, documentType));
    }
}
