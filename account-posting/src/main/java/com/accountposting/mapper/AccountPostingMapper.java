package com.accountposting.mapper;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.entity.AccountPostingEntity;
import com.accountposting.entity.enums.PostingStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {PostingStatus.class}
)
public interface AccountPostingMapper {

    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "status", expression = "java(PostingStatus.PENDING)")
    @Mapping(target = "requestPayload", ignore = true)
    @Mapping(target = "responsePayload", ignore = true)
    @Mapping(target = "targetSystems", ignore = true)
    AccountPostingEntity toEntity(AccountPostingRequest request);

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingResponse toResponse(AccountPostingEntity posting);

    @Mapping(source = "postingLegId", target = "postingLegId")
    @Mapping(source = "targetSystem", target = "name")
    @Mapping(source = "operation", target = "type")
    @Mapping(source = "legOrder", target = "legOrder")
    @Mapping(source = "status", target = "status")
    @Mapping(source = "mode", target = "mode")
    LegResponse toLegResponse(AccountPostingLegResponse legResponse);

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingCreateResponse toCreateResponse(AccountPostingEntity posting);
}
