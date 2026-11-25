package com.ptutor.backend.controller;

import java.time.LocalDate;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ptutor.backend.dto.enums.StatisticsGranularity;
import com.ptutor.backend.dto.response.StatisticsReportResponse;
import com.ptutor.backend.exception.ApiException;
import com.ptutor.backend.response.ApiResponse;
import com.ptutor.backend.response.ApiResponseFactory;
import com.ptutor.backend.service.AdminStatisticsService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/v1/admin/statistics")
@RequiredArgsConstructor
public class AdminStatisticsController {

    private static final String BASE_PATH = "/api/v1/admin/statistics";

    private final AdminStatisticsService statisticsService;
    private final ApiResponseFactory responseFactory;

    @GetMapping("/{report}")
    public ResponseEntity<ApiResponse<StatisticsReportResponse>> report(
            @PathVariable String report,
            @RequestParam(required = false) LocalDate fromDate,
            @RequestParam(required = false) LocalDate toDate,
            @RequestParam(defaultValue = "Asia/Ho_Chi_Minh") String timezone,
            @RequestParam(required = false) StatisticsGranularity granularity) {
        String normalized = report == null ? "" : report.strip().toLowerCase(java.util.Locale.ROOT);
        StatisticsReportResponse data = switch (normalized) {
            case "overview" -> statisticsService.overview(fromDate, toDate, timezone, granularity);
            case "students" -> statisticsService.students(fromDate, toDate, timezone, granularity);
            case "tutors" -> statisticsService.tutors(fromDate, toDate, timezone, granularity);
            case "studying-requests" -> statisticsService.studyingRequests(fromDate, toDate, timezone, granularity);
            case "teaching-requests" -> statisticsService.teachingRequests(fromDate, toDate, timezone, granularity);
            case "matching" -> statisticsService.matching(fromDate, toDate, timezone, granularity);
            case "contracts" -> statisticsService.contracts(fromDate, toDate, timezone, granularity);
            case "lessons" -> statisticsService.lessons(fromDate, toDate, timezone, granularity);
            case "finance" -> statisticsService.finance(fromDate, toDate, timezone, granularity);
            case "payments" -> statisticsService.payments(fromDate, toDate, timezone, granularity);
            case "installments" -> statisticsService.installments(fromDate, toDate, timezone, granularity);
            case "wallets" -> statisticsService.wallets(fromDate, toDate, timezone, granularity);
            case "withdrawals" -> statisticsService.withdrawals(fromDate, toDate, timezone, granularity);
            case "employees" -> statisticsService.employees(fromDate, toDate, timezone, granularity);
            default -> throw new ApiException(
                    HttpStatus.NOT_FOUND, "STATISTICS_REPORT_NOT_FOUND", "Statistics report not found");
        };
        return ResponseEntity.ok(responseFactory.success(data, BASE_PATH + "/" + normalized));
    }
}
