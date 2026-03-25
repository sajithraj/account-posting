package com.accountposting.dto.accountposting;

import com.accountposting.entity.enums.PostingStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AccountPostingSearchRequestV2 {

    private PostingStatus status;
    private String endToEndReferenceId;
    private String sourceReferenceId;
    private String sourceName;
    private String requestType;

    private String targetSystem;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
