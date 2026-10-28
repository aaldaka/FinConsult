package com.example.finConsult.model.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@AllArgsConstructor //requireargs doesnt work for some reason
@NoArgsConstructor
public class AuditLogResponse { //avoids leaking user info, just IDs
    private Long id;
    private String actorEmail;   //"SYSTEM" if actor is null
    private String actorName;    //"System" if actor is null
    private String action;
    private String entityType;
    private Long entityId;
    private String details;
    private LocalDateTime timestamp;
}
