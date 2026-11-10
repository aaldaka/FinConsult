package com.example.userservice.controller;

import com.example.userservice.model.request.PromoteAdvisorRequest;
import com.example.userservice.model.response.InternalAdvisorResponse;
import com.example.userservice.model.response.PromoteAdvisorResponse;
import com.example.userservice.service.InternalService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/** Advisor lookup and promotion for the other services. Protected by the internal key, see InternalKeyFilter. */
@RestController
@RequestMapping("/internal/advisors")
@RequiredArgsConstructor
public class InternalAdvisorController {

    private final InternalService internalService;

    @GetMapping("/{id}")
    public InternalAdvisorResponse getAdvisor(@PathVariable Long id) {
        return internalService.getAdvisor(id);
    }

    @GetMapping("/by-user/{userId}")
    public InternalAdvisorResponse getAdvisorByUser(@PathVariable Long userId) {
        return internalService.getAdvisorByUser(userId);
    }

    @PostMapping("/lookup")
    public Map<Long, InternalAdvisorResponse> lookupAdvisors(@RequestBody List<Long> ids) {
        return internalService.lookupAdvisors(ids);
    }

    @PostMapping("/promote")
    public ResponseEntity<PromoteAdvisorResponse> promote(@Valid @RequestBody PromoteAdvisorRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(internalService.promote(request));
    }
}
