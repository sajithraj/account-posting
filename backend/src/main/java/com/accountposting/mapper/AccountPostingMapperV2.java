package com.accountposting.mapper;

import com.accountposting.dto.accountposting.AccountPostingCreateResponseV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountposting.AccountPostingResponseV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.AccountPostingHistoryEntity;
import com.accountposting.entity.enums.PostingStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {PostingStatus.class}
)
public interface AccountPostingMapperV2 {

    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "status", expression = "java(PostingStatus.PNDG)")
    @Mapping(target = "requestPayload", ignore = true)
    @Mapping(target = "responsePayload", ignore = true)
    @Mapping(target = "targetSystems", ignore = true)
    AccountPostingEntity toEntity(AccountPostingRequestV2 request);

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingResponseV2 toResponse(AccountPostingEntity posting);

    @Mapping(source = "postingLegId", target = "postingLegId")
    @Mapping(source = "targetSystem", target = "name")
    @Mapping(source = "operation", target = "type")
    @Mapping(source = "legOrder", target = "legOrder")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "mode", target = "mode")
    LegResponseV2 toLegResponse(AccountPostingLegResponseV2 legResponse);

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingCreateResponseV2 toCreateResponse(AccountPostingEntity posting);

    /**
     * Maps a history row to the same response DTO used for active postings.
     */
    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingResponseV2 toResponseFromHistory(AccountPostingHistoryEntity history);
}
