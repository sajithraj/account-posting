package com.accountposting.dto.accountposting;

import com.accountposting.entity.enums.PostingStatus;
import lombok.Data;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

@Data
public class AccountPostingSearchRequest {

    private PostingStatus status;
    private String endToEndReferenceId;
    private String sourceReferenceId;
    private String sourceName;
    private String requestType;

    /**
     * Substring match against the target_systems column (e.g. "CBS" matches "CBS_GL").
     */
    private String targetSystem;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate fromDate;

    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate toDate;
}
