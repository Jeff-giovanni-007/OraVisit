package com.orabank.backend.dto;

import com.orabank.backend.entity.Direction;
import com.orabank.backend.entity.Visit;
import com.orabank.backend.entity.VisitStatus;
import java.time.Duration;
import java.time.Instant;

public record VisitResponse(
        Long id,
        VisitorSummaryResponse visitor,
        EmployeeSummaryResponse hostEmployee,
        EmployeeSummaryResponse registeredBy,
        Direction direction,
        String purpose,
        String badgeNumber,
        VisitStatus status,
        Instant checkInAt,
        Instant checkOutAt,
        Instant cancelledAt,
        String cancellationReason,
        Long durationMinutes) {

    public static VisitResponse from(Visit visit, boolean visitorHasIdentityDocument) {
        return new VisitResponse(
                visit.getId(),
                VisitorSummaryResponse.from(visit.getVisitor(), visitorHasIdentityDocument),
                EmployeeSummaryResponse.from(visit.getHostEmployee()),
                EmployeeSummaryResponse.from(visit.getRegisteredBy()),
                visit.getDirection(),
                visit.getPurpose(),
                visit.getBadgeNumber(),
                visit.getStatus(),
                visit.getCheckInAt(),
                visit.getCheckOutAt(),
                visit.getCancelledAt(),
                visit.getCancellationReason(),
                durationMinutes(visit));
    }

    private static Long durationMinutes(Visit visit) {
        if (visit.getCheckOutAt() == null) {
            return null;
        }
        return Duration.between(visit.getCheckInAt(), visit.getCheckOutAt()).toMinutes();
    }
}
