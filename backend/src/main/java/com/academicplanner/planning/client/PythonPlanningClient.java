package com.academicplanner.planning.client;

import com.academicplanner.exception.PlanningServiceUnavailableException;
import com.academicplanner.planning.dto.PlanningRequestDto;
import com.academicplanner.planning.dto.PlanningResponseDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Slf4j
@Component
public class PythonPlanningClient {

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    public PythonPlanningClient(
            RestClient pythonPlanningRestClient,
            ObjectMapper objectMapper) {
        this.restClient = pythonPlanningRestClient;
        this.objectMapper = objectMapper;
    }

    public PlanningResponseDto generatePlan(PlanningRequestDto request) {
        try {
            String json = objectMapper.writeValueAsString(request);

            log.info("========== PYTHON PLANNING REQUEST ==========");
            log.info("{}", json);
            log.info("=============================================");

            PlanningResponseDto response = restClient.post()
                    .uri("/api/v1/plan")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(json)
                    .retrieve()
                    .body(PlanningResponseDto.class);

            if (response == null) {
                throw new PlanningServiceUnavailableException(
                        "Python planning service returned an empty response");
            }

            return response;

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize planning request", ex);
            throw new PlanningServiceUnavailableException(
                    "Failed to serialize planning request: " + ex.getMessage(), ex);

        } catch (RestClientException ex) {
            log.error("Call to Python planning service failed", ex);
            throw new PlanningServiceUnavailableException(
                    "Unable to reach the Python planning service: " + ex.getMessage(), ex);
        }
    }
}