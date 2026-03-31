package com.sajith.payments.redesign.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SearchOrderBy {

    @JsonProperty("property")
    private String property;

    @JsonProperty("sort_order")
    private String sortOrder;
}
