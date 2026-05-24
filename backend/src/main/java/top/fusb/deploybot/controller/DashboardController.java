package top.fusb.deploybot.controller;

import lombok.RequiredArgsConstructor;
import top.fusb.deploybot.dto.DashboardAnalytics;
import top.fusb.deploybot.dto.DashboardQuery;
import top.fusb.deploybot.dto.DashboardSummary;
import top.fusb.deploybot.service.DashboardService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping("/summary")
    public DashboardSummary summary() {
        return dashboardService.buildSummary();
    }

    @GetMapping("/analytics")
    public DashboardAnalytics analytics(@ModelAttribute DashboardQuery query) {
        return dashboardService.buildAnalytics(query);
    }
}
