package com.sr.accountposting.dto.posting;

import com.sr.accountposting.enums.PostingStatus;
import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ProcessingResult {

    private PostingStatus status;
    private String reason;
    private String updatedAt;
    private List<LegFailure> failures;

    @Data
    @Builder
    public static class LegFailure {
        private String targetSystem;
        private String reason;
    }
}
