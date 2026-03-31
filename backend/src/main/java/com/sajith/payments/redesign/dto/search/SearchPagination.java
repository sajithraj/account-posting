package com.sajith.payments.redesign.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class SearchPagination {

    /**
     * 1-based starting position of the result set. Default 1.
     */
    @JsonProperty("offset")
    private int offset = 1;

    /**
     * Maximum number of records to return. Default 20.
     */
    @JsonProperty("limit")
    private int limit = 20;
}
