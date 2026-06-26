package com.projvault.observability;

import com.projvault.common.ApiResponse;
import com.projvault.security.RequirePerm;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pkc")
public class ObservabilityController {
    private final ObservabilityService service;
    public ObservabilityController(ObservabilityService service) { this.service = service; }

    @GetMapping("/observability")
    @RequirePerm("pkc:observability:view")
    public ApiResponse<ObservabilityService.ObservabilitySnapshot> snapshot() {
        return ApiResponse.ok(service.snapshot());
    }
}
