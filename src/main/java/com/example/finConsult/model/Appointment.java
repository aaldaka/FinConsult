package com.example.finConsult.model;

import com.example.finConsult.model.enums.AppointmentStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

@Entity
@Getter
@Setter
@Table(name = "appointments")
public class Appointment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "client_id", nullable = false)
    private User client;

    @ManyToOne
    @JoinColumn(name = "advisor_id", nullable = false)
    private Advisor advisor;

    @ManyToOne
    @JoinColumn(name = "service_type_id", nullable = false)
    private ServiceType serviceType;

    @ManyToOne(optional = false)
    @JoinColumn(name = "slot_id", nullable = false)
    private AvailabilitySlot slot;

    @Enumerated(EnumType.STRING)
    private AppointmentStatus status = AppointmentStatus.PENDING;

    @Column
    private String notes;

    @Column
    private String cancellationReason;

    @Column
    private LocalDateTime cancelledAt;

    private boolean excused = false;

    @Column
    private String excuseReason;

    @Version
    @ColumnDefault("0")
    private Long version; // advisor confirms while the client cancels
}