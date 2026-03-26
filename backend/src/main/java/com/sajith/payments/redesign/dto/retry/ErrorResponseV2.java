package com.sajith.payments.redesign.dto.retry;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ErrorResponseV2 {

    @JsonProperty("id")
    private final String id;

    @JsonProperty("name")
    private final String name;

    @JsonProperty("message")
    private final String message;

    @JsonProperty("errors")
    private final List<FieldError> errors;

    @Getter
    @Builder
    public static class FieldError {

        @JsonProperty("field")
        private final String field;

        @JsonProperty("message")
        private final String message;

        @JsonProperty("rejected_value")
        private final Object rejectedValue;
    }
}
