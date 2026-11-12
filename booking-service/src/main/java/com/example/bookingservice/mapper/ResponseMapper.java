package com.example.bookingservice.mapper;

import com.example.bookingservice.model.AvailabilitySlot;
import com.example.bookingservice.model.ServiceType;
import com.example.bookingservice.model.response.ServiceTypeResponse;
import com.example.bookingservice.model.response.SlotResponse;
import org.springframework.stereotype.Component;

@Component
public class ResponseMapper {

    public SlotResponse toSlot(AvailabilitySlot s) {
        return new SlotResponse(s.getId(), s.getAdvisorId(),
                s.getStartTime(), s.getEndTime(), s.getStatus());
    }

    public ServiceTypeResponse toServiceType(ServiceType s) {
        return new ServiceTypeResponse(s.getId(), s.getName(), s.getDescription(), s.getDurationMinutes(),
                s.isRequiresKyc(), s.getRequiredDocumentType(), s.getRequiredSpecialty());
    }
}
