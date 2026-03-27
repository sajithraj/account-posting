package com.sajith.payments.redesign.dto.accountposting;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.datatype.jsr310.deser.LocalDateDeserializer;
import com.fasterxml.jackson.datatype.jsr310.ser.LocalDateSerializer;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AccountPostingSearchRequestV2 {

    @JsonProperty("status")
    private PostingStatus status;

    @JsonProperty("end_to_end_reference_id")
    private String endToEndReferenceId;

    @JsonProperty("source_reference_id")
    private String sourceReferenceId;

    @JsonProperty("source_name")
    private String sourceName;

    @JsonProperty("request_type")
    private String requestType;

    @JsonProperty("target_system")
    private String targetSystem;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonProperty("from_date")
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd")
    @JsonSerialize(using = LocalDateSerializer.class)
    @JsonDeserialize(using = LocalDateDeserializer.class)
    @JsonProperty("to_date")
    private LocalDate toDate;
}
