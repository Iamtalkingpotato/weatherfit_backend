package com.weatherfit.weatherfitBackend.controller;

import com.weatherfit.weatherfitBackend.domain.entity.ProcessFailLog;
import com.weatherfit.weatherfitBackend.domain.entity.ProcessSuccessLog;
import com.weatherfit.weatherfitBackend.domain.repository.ProcessFailLogRepository;
import com.weatherfit.weatherfitBackend.domain.repository.ProcessSuccessLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class LogController {

    private final ProcessSuccessLogRepository successLogRepository;
    private final ProcessFailLogRepository failLogRepository;

    // 성공 로그 조회 (최신순 100건)
    @GetMapping("/success")
    public List<ProcessSuccessLog> getSuccessLogs() {
        return successLogRepository.findAll(
            PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "processedAt"))
        ).getContent();
    }

    // 실패 로그 조회 (최신순 100건)
    @GetMapping("/fail")
    public List<ProcessFailLog> getFailLogs() {
        return failLogRepository.findAll(
            PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "processedAt"))
        ).getContent();
    }

    // 통계
    @GetMapping("/stats")
    public java.util.Map<String, Object> getStats() {
        java.util.Map<String, Object> result = new java.util.HashMap<>();
        result.put("successCount", successLogRepository.count());
        result.put("failCount", failLogRepository.count());
        return result;
    }
}