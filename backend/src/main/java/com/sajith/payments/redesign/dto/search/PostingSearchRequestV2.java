package com.sajith.payments.redesign.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import java.util.List;

@Data
public class PostingSearchRequestV2 {

    @JsonProperty("projected_properties")
    private List<String> projectedProperties;

    @JsonProperty("conditions")
    private List<SearchCondition> conditions;

    @JsonProperty("order_by")
    private List<SearchOrderBy> orderBy;

    @JsonProperty("pagination")
    private SearchPagination pagination;
}
