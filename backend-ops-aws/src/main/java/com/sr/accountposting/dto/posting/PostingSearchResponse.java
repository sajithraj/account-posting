package com.sr.accountposting.dto.posting;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class PostingSearchResponse {

    @JsonProperty("items")
    private List<PostingResponse> items;

    @JsonProperty("next_page_token")
    private String nextPageToken;
}
