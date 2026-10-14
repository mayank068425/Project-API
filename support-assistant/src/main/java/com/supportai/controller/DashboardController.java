package com.supportai.controller;

import com.supportai.dto.response.ApiResponse;
import com.supportai.dto.response.DashboardResponse;
import com.supportai.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
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

    @GetMapping
    @Operation(summary = "Response metrics and assistant health",
            description = "Median, mean and p90 together: one incident left open over a "
                    + "weekend drags the mean far above anything typical. Empty samples "
                    + "report null rather than zero, because zero reads as 'instant'.")
    public ApiResponse<DashboardResponse.Overview> overview() {
        DashboardResponse.Overview overview = dashboardService.overview();
        return ApiResponse.ok(overview, overview.assistant().notes().isEmpty() ? null
                : String.join(" ", overview.assistant().notes()));
    }
}
