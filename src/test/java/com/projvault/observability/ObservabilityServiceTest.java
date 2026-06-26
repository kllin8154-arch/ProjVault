package com.projvault.observability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ObservabilityServiceTest {
    @Test
    void aggregatesDynamicIdsIntoStableRoute() {
        ObservabilityService service = new ObservabilityService();
        service.record("GET", "/api/pkc/artifacts/10/preview", 200, 12, null);
        service.record("GET", "/api/pkc/artifacts/11/preview", 500, 30, new IllegalStateException("boom"));

        ObservabilityService.ObservabilitySnapshot snapshot = service.snapshot();

        assertThat(snapshot.requests()).isEqualTo(2);
        assertThat(snapshot.errors()).isEqualTo(1);
        assertThat(snapshot.routes()).singleElement().satisfies(metric -> {
            assertThat(metric.route()).isEqualTo("GET /api/pkc/artifacts/{id}/preview");
            assertThat(metric.averageMs()).isEqualTo(21);
        });
        assertThat(snapshot.recentFailures()).hasSize(1);
    }
}
