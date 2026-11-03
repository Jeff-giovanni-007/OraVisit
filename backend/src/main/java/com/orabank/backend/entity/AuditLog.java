package com.orabank.backend.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "audit_logs")
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40, updatable = false)
    private AuditAction action;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10, updatable = false)
    private AuditOutcome outcome;

    @Column(name = "actor_id", updatable = false)
    private Long actorId;

    @Column(name = "actor_matricule", nullable = false, length = 40, updatable = false)
    private String actorMatricule;

    @Column(name = "actor_name", length = 140, updatable = false)
    private String actorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_role", length = 20, updatable = false)
    private Role actorRole;

    @Enumerated(EnumType.STRING)
    @Column(name = "target_type", nullable = false, length = 30, updatable = false)
    private AuditTarget targetType;

    @Column(name = "target_id", updatable = false)
    private Long targetId;

    @Column(name = "target_label", length = 160, updatable = false)
    private String targetLabel;

    @Column(length = 500, updatable = false)
    private String details;

    @Column(name = "ip_address", length = 45, updatable = false)
    private String ipAddress;
}
