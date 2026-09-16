package com.academicplanner.planning.client;

import com.academicplanner.exception.PlanningServiceUnavailableException;
import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.dto.PlanningResponseDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class PythonPlanningClient {

    private final RestClient restClient;

    public PythonPlanningClient(RestClient pythonPlanningRestClient) {
        this.restClient = pythonPlanningRestClient;
    }

    public PlanningResponseDto generatePlan(PlanningRequestDto request) {
        try {
            log.info("Sending planning request to Python service: {}", request);

            PlanningResponseDto response = restClient.post()
                    .uri("/api/v1/plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(request)
                    .retrieve()
                    .body(PlanningResponseDto.class);

            if (response == null) {
                throw new PlanningServiceUnavailableException(
                        "Python planning service returned an empty body");
            }

            return response;

        } catch (RestClientException ex) {
            log.error("Call to Python planning service failed", ex);

            throw new PlanningServiceUnavailableException(
                    "Unable to reach the Python planning service: " + ex.getMessage(),
                    ex);
        }
    }
}