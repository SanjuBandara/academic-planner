package com.academicplanner.planning.client;

import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.dto.PlanningResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

/**
 * Thin HTTP client for the Python planning service's {@code POST /api/v1/plan}
 * endpoint. Contains no scheduling logic itself — only request/response
 * transport and error translation.
 *
 * <pre>
 *   PlanningService (Spring)
 *        ↓
 *   PythonPlanningClient   ← this class
 *        ↓ HTTP
 *   Python Planning API (FastAPI + CP-SAT)
 * </pre>
 */
@Slf4j
@Component
public class PythonPlanningClient {

    private final RestClient restClient;

    public PythonPlanningClient(RestClient pythonPlanningRestClient) {
        this.restClient = pythonPlanningRestClient;
    }

    /**
     * Calls the Python planning service and returns its response.
     *
     * @throws PlanningServiceUnavailableException if the service cannot be
     *                                             reached or returns a
     *                                             non-2xx/4xx-parseable response.
     */
    public PlanningResponseDto generatePlan(PlanningRequestDto request) {
        try {
            PlanningResponseDto response = restClient.post()
                    .uri("/api/v1/plan")
                    .body(request)
                    .retrieve()
                    .body(PlanningResponseDto.class);

            if (response == null) {
                throw new PlanningServiceUnavailableException("Python planning service returned an empty body");
            }
            return response;
        } catch (RestClientException ex) {
            log.error("Call to Python planning service failed", ex);
            throw new PlanningServiceUnavailableException("Unable to reach the Python planning service", ex);
        }
    }

    /**
     * Thrown when the Python planning service cannot be reached or fails
     * unexpectedly.
     */
    public static class PlanningServiceUnavailableException extends RuntimeException {
        public PlanningServiceUnavailableException(String message) {
            super(message);
        }

        public PlanningServiceUnavailableException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
