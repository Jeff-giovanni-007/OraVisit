package com.orabank.backend.dto;

public record DashboardResponse(
        long visitsToday,
        long activeVisits,
        long completedToday,
        long cancelledToday,
        Long totalVisitors,
        Long totalVisits,
        Long totalEmployees) {
}
