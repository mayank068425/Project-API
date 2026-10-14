package com.example.lms.controller;

import com.example.lms.dto.response.ApiResponse;
import com.example.lms.dto.response.DashboardResponse;
import com.example.lms.service.DashboardService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@Tag(name = "Dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/admin")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<DashboardResponse.Admin> admin() {
        DashboardResponse.Admin data = dashboardService.adminDashboard();
        return ApiResponse.ok(data, data.unreconciledBooks() > 0
                ? data.unreconciledBooks() + " book(s) have copy counts that disagree with "
                        + "the loan table. Investigate before trusting availability."
                : null);
    }

    @GetMapping("/librarian")
    @PreAuthorize("hasAnyRole('LIBRARIAN','ADMIN')")
    public ApiResponse<DashboardResponse.Librarian> librarian() {
        return ApiResponse.ok(dashboardService.librarianDashboard());
    }
}
