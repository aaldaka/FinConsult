package com.example.bookingservice.controller;

import com.example.bookingservice.model.response.ServiceTypeResponse;
import com.example.bookingservice.service.ServiceTypeService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/service-types")
@RequiredArgsConstructor
public class ServiceTypeController {

    private final ServiceTypeService serviceTypeService;

    @GetMapping
    public ResponseEntity<List<ServiceTypeResponse>> listActive() {
        return ResponseEntity.ok(serviceTypeService.getActiveServices());
    }
}
