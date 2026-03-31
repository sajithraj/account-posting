package com.sajith.payments.redesign.mapper;

import com.sajith.payments.redesign.dto.ExternalCallResultV2;
import com.sajith.payments.redesign.dto.accountposting.IncomingPostingRequest;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegRequestV2;
import com.sajith.payments.redesign.dto.accountpostingleg.AccountPostingLegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegCreateResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.LegResponseV2;
import com.sajith.payments.redesign.dto.accountpostingleg.UpdateLegRequestV2;
import com.sajith.payments.redesign.entity.AccountPostingLegEntity;
import com.sajith.payments.redesign.entity.AccountPostingLegHistoryEntity;
import com.sajith.payments.redesign.entity.enums.LegMode;
import com.sajith.payments.redesign.entity.enums.LegStatus;
import org.mapstruct.BeanMapping;
import org.mapstruct.Builder;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import java.time.Instant;
import java.util.List;

@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface AccountPostingLegMapperV2 {

    @BeanMapping(builder = @Builder(disableBuilder = true))
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "attemptNumber", constant = "1")
    @Mapping(target = "responsePayload", ignore = true)
    @Mapping(target = "status", expression = "java(resolveStatus(request))")
    @Mapping(target = "mode", expression = "java(resolveMode(request))")
    @Mapping(target = "operation", expression = "java(resolveOperation(request))")
    @Mapping(target = "createdBy", constant = "SYSTEM")
    @Mapping(target = "updatedBy", constant = "SYSTEM")
    AccountPostingLegEntity toEntity(AccountPostingLegRequestV2 request);

    AccountPostingLegResponseV2 toResponse(AccountPostingLegEntity leg);

    LegCreateResponseV2 toLegCreateResponse(LegResponseV2 leg);

    List<LegCreateResponseV2> toLegCreateResponses(List<LegResponseV2> legs);

    AccountPostingLegResponseV2 toResponseFromHistory(AccountPostingLegHistoryEntity leg);

    @Mapping(target = "archivedAt", source = "archivedAt")
    AccountPostingLegHistoryEntity toLegHistory(AccountPostingLegEntity src, Instant archivedAt);

    @Mapping(target = "account", source = "request.debtorAccount")
    @Mapping(target = "transactionOrder", source = "transactionOrder")
    @Mapping(target = "targetSystem", source = "targetSystem")
    @Mapping(target = "mode", source = "mode")
    @Mapping(target = "operation", source = "operation")
    @Mapping(target = "requestPayload", source = "requestPayload")
    AccountPostingLegRequestV2 toCreateLegRequest(
            IncomingPostingRequest request,
            Integer transactionOrder,
            String targetSystem,
            LegMode mode,
            String operation,
            String requestPayload);

    @Mapping(target = "postedTime", expression = "java(parsePostedTime(result.postedTime()))")
    UpdateLegRequestV2 toUpdateLegRequest(ExternalCallResultV2 result);

    default Instant parsePostedTime(String postedTime) {
        if (postedTime == null || postedTime.isBlank()) return Instant.now();
        try {
            return Instant.parse(postedTime);
        } catch (Exception e) {
            return Instant.now();
        }
    }

    @BeanMapping(nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "transactionId", ignore = true)
    @Mapping(target = "postingId", ignore = true)
    @Mapping(target = "transactionOrder", ignore = true)
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
