package com.orabank.backend.controller;

import com.orabank.backend.dto.DashboardResponse;
import com.orabank.backend.service.DashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN', 'RECEPTION')")
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    public DashboardResponse load() {
        return dashboardService.load();
    }
}
