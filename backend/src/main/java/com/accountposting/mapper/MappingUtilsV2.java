package com.accountposting.mapper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Shared JSON serialisation helper used by MapStruct mappers (via {@code uses = {MappingUtilsV2.class}})
 * and any service that needs a safe {@code toJson} without duplicating ObjectMapper boilerplate.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MappingUtilsV2 {

    private final ObjectMapper objectMapper;

    /**
     * Serialises {@code obj} to a JSON string. Returns {@code "{}"} on failure so that
     * JSON columns are never left null due to a serialisation error.
     */
    public String toJson(Object obj) {
        if (obj == null) return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException ex) {
            log.warn("Could not serialise {} to JSON", obj.getClass().getSimpleName(), ex);
            return "{}";
        }
    }
}
