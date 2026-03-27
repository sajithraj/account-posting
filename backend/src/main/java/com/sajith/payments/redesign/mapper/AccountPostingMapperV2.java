package com.sajith.payments.redesign.mapper;

import com.sajith.payments.redesign.dto.accountposting.AccountPostingCreateResponseV2;
import com.sajith.payments.redesign.dto.accountposting.AccountPostingFullResponseV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.entity.AccountPostingEntity;
import com.sajith.payments.redesign.entity.AccountPostingHistoryEntity;
import com.sajith.payments.redesign.entity.enums.CreditDebitIndicator;
import com.sajith.payments.redesign.entity.enums.PostingStatus;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;

@Mapper(
        componentModel = "spring",
        unmappedTargetPolicy = ReportingPolicy.IGNORE,
        imports = {PostingStatus.class, CreditDebitIndicator.class}
)
public interface AccountPostingMapperV2 {

    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "status", expression = "java(PostingStatus.PNDG)")
    @Mapping(target = "requestPayload", ignore = true)
    @Mapping(target = "responsePayload", ignore = true)
    @Mapping(target = "targetSystems", ignore = true)
    @Mapping(target = "sourceReferenceId", source = "sourceRefId")
    @Mapping(target = "endToEndReferenceId", source = "endToEndRefId")
    @Mapping(target = "amount", expression = "java(new java.math.BigDecimal(request.getAmount().getValue()))")
    @Mapping(target = "currency", expression = "java(request.getAmount().getCurrency())")
    @Mapping(target = "creditDebitIndicator", expression = "java(CreditDebitIndicator.valueOf(request.getCreditDebitIndicator().toUpperCase()))")
    @Mapping(target = "requestedExecutionDate", expression = "java(java.time.LocalDate.parse(request.getRequestedExecutionDate()))")
    AccountPostingEntity toEntity(IncomingPostingRequest request);

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingFullResponseV2 toResponse(AccountPostingEntity posting);

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

    @Mapping(source = "status", target = "postingStatus")
    @Mapping(target = "processedAt", ignore = true)
    @Mapping(target = "responses", ignore = true)
    AccountPostingFullResponseV2 toResponseFromHistory(AccountPostingHistoryEntity history);

    @Mapping(target = "archivedAt", source = "archivedAt")
    AccountPostingHistoryEntity toHistory(AccountPostingEntity src, Instant archivedAt);
}
