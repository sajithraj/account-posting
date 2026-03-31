package com.sajith.payments.redesign.dto.search;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostingSearchResponseV2 {

    @JsonProperty("items")
    private List<AccountPostingFullResponseV2> items;

    @JsonProperty("total_items")
    private long totalItems;

    @JsonProperty("offset")
    private int offset;

    @JsonProperty("limit")
    private int limit;
}
