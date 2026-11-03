package com.orabank.backend.dto;

import com.orabank.backend.entity.AuditAction;
import com.orabank.backend.entity.AuditLog;
import com.orabank.backend.entity.AuditOutcome;
import com.orabank.backend.entity.AuditTarget;
import com.orabank.backend.entity.Role;
import java.time.Instant;

public record AuditLogResponse(
        Long id,
        Instant occurredAt,
        AuditAction action,
        AuditOutcome outcome,
        Long actorId,
        String actorMatricule,
        String actorName,
        Role actorRole,
        AuditTarget targetType,
        Long targetId,
        String targetLabel,
        String details,
        String ipAddress) {

    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(
                log.getId(),
                log.getOccurredAt(),
                log.getAction(),
                log.getOutcome(),
                log.getActorId(),
                log.getActorMatricule(),
                log.getActorName(),
                log.getActorRole(),
                log.getTargetType(),
                log.getTargetId(),
                log.getTargetLabel(),
                log.getDetails(),
                log.getIpAddress());
    }
}
