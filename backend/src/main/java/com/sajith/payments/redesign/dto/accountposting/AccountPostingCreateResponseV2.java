package com.sajith.payments.redesign.dto.accountposting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.InstantDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.InstantSerializer;
import com.sajith.payments.redesign.dto.accountpostingleg.LegCreateResponseV2;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.List;

@Data
@Builder
public class AccountPostingCreateResponseV2 {

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("posting_status")
    private PostingStatus postingStatus;

    @JsonProperty("processed_at")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonSerialize(using = InstantSerializer.class)
    @JsonDeserialize(using = InstantDeserializer.class)
    private Instant processedAt;

    @JsonProperty("responses")
    private List<LegCreateResponseV2> responses;
}
