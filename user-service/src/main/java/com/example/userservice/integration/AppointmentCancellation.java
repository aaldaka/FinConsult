package com.example.userservice.integration;

/**
 * Cancels appointments. Appointments belong to the booking side, so this service only asks for it.
 * In the main app, deactivating a user or an advisor cancelled their upcoming (pending and confirmed)
 * appointments, freed the slots and notified the other side. RestAppointmentCancellation asks the booking
 * service to do it.
 */
public interface AppointmentCancellation {

    /**
     * Cancels the upcoming appointments of a client who was deactivated by an admin.
     * In the main app this went through the admin cancel path, so no strike was added.
     *
     * @return how many appointments were cancelled
     */
    int cancelUpcomingOfClient(Long clientUserId, Long adminUserId, String reason);

    /**
     * Cancels the upcoming appointments of an advisor who was deactivated, as if the advisor cancelled them.
     *
     * @return how many appointments were cancelled
     */
    int cancelUpcomingOfAdvisor(Long advisorId, Long advisorUserId, String reason);
}
