package com.sajith.payments.redesign.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class SearchCondition {

    @JsonProperty("property")
    private String property;

    @JsonProperty("operator")
    private String operator;

    @JsonProperty("values")
    private List<String> values;
}
