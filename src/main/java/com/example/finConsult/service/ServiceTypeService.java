package com.example.finConsult.service;

import com.example.finConsult.mapper.ResponseMapper;
import com.example.finConsult.model.response.ServiceTypeResponse;
import com.example.finConsult.repository.ServiceTypeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ServiceTypeService {

    private final ServiceTypeRepository serviceTypeRepository;
    private final ResponseMapper mapper;

    public List<ServiceTypeResponse> getActiveServices() {
        return serviceTypeRepository.findByActiveTrue().stream().map(mapper::toServiceType).toList();
    }
}
