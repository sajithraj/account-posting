package com.accountposting.mapper;

import com.accountposting.dto.ExternalCallResultV2;
import com.accountposting.dto.accountposting.AccountPostingRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.accountposting.dto.accountpostingleg.LegCreateResponseV2;
import com.accountposting.dto.accountpostingleg.LegResponseV2;
import com.accountposting.dto.accountpostingleg.UpdateLegRequestV2;
import com.accountposting.entity.AccountPostingLegEntity;
import com.accountposting.entity.AccountPostingLegHistoryEntity;
import com.accountposting.entity.enums.LegMode;
import com.accountposting.entity.enums.LegStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountPostingLegMapperV2 {

    @Mapping(target = "postingLegId", ignore = true)
    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "attemptNumber", constant = "1")
    @Mapping(target = "responsePayload", ignore = true)
    @Mapping(target = "status", expression = "java(resolveStatus(request))")
    @Mapping(target = "mode", expression = "java(resolveMode(request))")
    @Mapping(target = "operation", expression = "java(resolveOperation(request))")
    AccountPostingLegEntity toEntity(AccountPostingLegRequestV2 request);

    AccountPostingLegResponseV2 toResponse(AccountPostingLegEntity leg);

    LegCreateResponseV2 toLegCreateResponse(LegResponseV2 leg);

    List<LegCreateResponseV2> toLegCreateResponses(List<LegResponseV2> legs);

    AccountPostingLegResponseV2 toResponseFromHistory(AccountPostingLegHistoryEntity leg);

    @Mapping(target = "archivedAt", source = "archivedAt")
    AccountPostingLegHistoryEntity toLegHistory(AccountPostingLegEntity src, Instant archivedAt);

    @Mapping(target = "account", source = "request.debtorAccount")
    @Mapping(target = "legOrder", source = "legOrder")
    @Mapping(target = "targetSystem", source = "targetSystem")
    @Mapping(target = "mode", source = "mode")
    @Mapping(target = "operation", source = "operation")
    @Mapping(target = "requestPayload", source = "requestPayload")
    AccountPostingLegRequestV2 toCreateLegRequest(
            AccountPostingRequestV2 request,
            Integer legOrder,
            String targetSystem,
            LegMode mode,
            String operation,
            String requestPayload);

    @Mapping(target = "postedTime", expression = "java(java.time.Instant.now())")
    UpdateLegRequestV2 toUpdateLegRequest(ExternalCallResultV2 result);

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "postingLegId", ignore = true)
    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "legOrder", ignore = true)
    @Mapping(target = "targetSystem", ignore = true)
    @Mapping(target = "account", ignore = true)
    @Mapping(target = "attemptNumber", ignore = true)
    @Mapping(target = "operation", ignore = true)
    void applyUpdate(UpdateLegRequestV2 request, @MappingTarget AccountPostingLegEntity leg);

    default LegStatus resolveStatus(AccountPostingLegRequestV2 request) {
        if (request.getStatus() == null) return LegStatus.PENDING;
        try {
            return LegStatus.valueOf(request.getStatus().toUpperCase());
        } catch (IllegalArgumentException e) {
            return LegStatus.PENDING;
        }
    }

    default LegMode resolveMode(AccountPostingLegRequestV2 request) {
        return request.getMode() != null ? request.getMode() : LegMode.NORM;
    }

    default String resolveOperation(AccountPostingLegRequestV2 request) {
        return request.getOperation() != null ? request.getOperation() : "POSTING";
    }
}
