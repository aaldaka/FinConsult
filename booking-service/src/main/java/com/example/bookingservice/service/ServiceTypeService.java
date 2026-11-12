package com.example.bookingservice.service;

import com.example.bookingservice.mapper.ResponseMapper;
import com.example.bookingservice.model.response.ServiceTypeResponse;
import com.example.bookingservice.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ResponseMapper mapper;

    @Transactional(readOnly = true)
    public List<ServiceTypeResponse> getActiveServices() {
        return serviceTypeRepository.findByActiveTrue().stream().map(mapper::toServiceType).toList();
    }
}
