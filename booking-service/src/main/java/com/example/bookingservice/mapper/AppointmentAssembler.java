package com.example.bookingservice.mapper;

import com.example.bookingservice.client.AdvisorClient;
import com.example.bookingservice.client.AdvisorInfo;
import com.example.bookingservice.client.UserClient;
import com.example.bookingservice.exception.ServiceUnavailable;
import com.example.bookingservice.model.Appointment;
import com.example.bookingservice.model.response.AppointmentResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

// builds the monolith's appointment json, the names come from the user service in one call per kind for a whole list
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentAssembler {

    private final AdvisorClient advisorClient;
    private final UserClient userClient;

    // used right after a change was saved: if the names cannot be fetched the change still succeeded,
    // so the answer goes out without them instead of a 503 that would make the caller try again
    public AppointmentResponse one(Appointment appointment) {
        try {
            return many(List.of(appointment)).get(0);
        } catch (ServiceUnavailable e) {
            log.warn("Appointment {} answered without names: {}", appointment.getId(), e.getMessage());
            return build(appointment, null, null);
        }
    }

    public List<AppointmentResponse> many(List<Appointment> appointments) {
        if (appointments.isEmpty()) {
            return List.of();
        }
        Map<Long, AdvisorInfo> advisors = advisorClient.lookup(appointments.stream().map(Appointment::getAdvisorId).toList());
        Map<Long, String> names = userClient.names(appointments.stream().map(Appointment::getClientId).toList());
        return appointments.stream()
                .map(a -> build(a, advisors.get(a.getAdvisorId()), names.get(a.getClientId())))
                .toList();
    }

    private AppointmentResponse build(Appointment a, AdvisorInfo advisor, String clientName) {
        return new AppointmentResponse(a.getId(), a.getStatus(), a.getNotes(),
                a.getCancellationReason(), a.isExcused(), a.getExcuseReason(),
                a.getAdvisorId(), advisor == null ? null : advisor.displayName(), advisor == null ? null : advisor.specialty(),
                clientName, a.getServiceType().getName(),
                a.getSlot().getStartTime(), a.getSlot().getEndTime(), a.getCreatedAt());
    }
}
