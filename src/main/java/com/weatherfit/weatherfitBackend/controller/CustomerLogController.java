package com.weatherfit.weatherfitBackend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.weatherfit.weatherfitBackend.domain.entity.Customer;
import com.weatherfit.weatherfitBackend.domain.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/admin/logs")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class CustomerLogController {

    private static final String CUSTOMER_TOPIC = "weatherfit.customer";

    private final CustomerRepository customerRepository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @PostMapping("/customer")
    public ResponseEntity<Map<String, Object>> receiveCustomerUpdate(@RequestBody Map<String, Object> body) {
        String eventType = String.valueOf(body.getOrDefault("event_type", ""));
        if (!"customer_update".equals(eventType)) {
            return ResponseEntity.badRequest().body(Map.of("error", "event_type must be customer_update"));
        }

        Object rawId = body.get("customer_id");
        if (rawId == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "customer_id is required"));
        }
        Long customerId;
        try {
            customerId = Long.parseLong(String.valueOf(rawId));
        } catch (NumberFormatException e) {
            return ResponseEntity.badRequest().body(Map.of("error", "customer_id must be a number"));
        }

        Object changedFieldsRaw = body.get("changed_fields");
        if (!(changedFieldsRaw instanceof Map)) {
            return ResponseEntity.badRequest().body(Map.of("error", "changed_fields is required"));
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> changedFields = (Map<String, Object>) changedFieldsRaw;

        Customer customer = customerRepository.findById(customerId).orElse(null);
        if (customer == null) {
            return ResponseEntity.notFound().build();
        }

        applyChangedFields(customer, changedFields);
        customerRepository.save(customer);
        log.info("고객 정보 업데이트 완료: customerId={}, 변경필드={}", customerId, changedFields.keySet());

        try {
            String message = objectMapper.writeValueAsString(body);
            kafkaTemplate.send(CUSTOMER_TOPIC, message);
            log.info("weatherfit.customer 토픽 발행 완료: customerId={}", customerId);
        } catch (Exception e) {
            log.error("weatherfit.customer 토픽 발행 실패: {}", e.getMessage());
        }

        Map<String, Object> result = new HashMap<>();
        result.put("customer_id", customerId);
        result.put("updated_fields", changedFields.keySet());
        result.put("message", "업데이트 성공");
        return ResponseEntity.ok(result);
    }

    private void applyChangedFields(Customer customer, Map<String, Object> fields) {
        if (hasValue(fields, "emailConsent"))
            customer.setEmailConsent(Boolean.parseBoolean(String.valueOf(fields.get("emailConsent"))));
        if (hasValue(fields, "marketingConsent"))
            customer.setMarketingConsent(Boolean.parseBoolean(String.valueOf(fields.get("marketingConsent"))));
        if (hasValue(fields, "pushConsent"))
            customer.setPushConsent(Boolean.parseBoolean(String.valueOf(fields.get("pushConsent"))));
        if (hasValue(fields, "smsConsent"))
            customer.setSmsConsent(Boolean.parseBoolean(String.valueOf(fields.get("smsConsent"))));
        if (hasValue(fields, "dmConsent"))
            customer.setDmConsent(Boolean.parseBoolean(String.valueOf(fields.get("dmConsent"))));
        if (hasValue(fields, "tmConsent"))
            customer.setTmConsent(Boolean.parseBoolean(String.valueOf(fields.get("tmConsent"))));
        if (hasValue(fields, "coldSensitivity"))
            customer.setColdSensitivity(Integer.parseInt(String.valueOf(fields.get("coldSensitivity"))));
        if (hasValue(fields, "activityLevel"))
            customer.setActivityLevel(String.valueOf(fields.get("activityLevel")));
        if (hasValue(fields, "preferredStyle"))
            customer.setPreferredStyle(String.valueOf(fields.get("preferredStyle")));
    }

    private boolean hasValue(Map<String, Object> fields, String key) {
        return fields.containsKey(key) && fields.get(key) != null;
    }
}
