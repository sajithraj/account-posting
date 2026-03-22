package com.accountposting.mapper;

import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegRequest;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.ExternalCallResult;
import com.accountposting.dto.accountpostingleg.UpdateLegRequest;
import com.accountposting.entity.AccountPostingLeg;
import com.accountposting.entity.enums.LegMode;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-22T11:35:03+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class AccountPostingLegMapperImpl implements AccountPostingLegMapper {

    @Override
    public AccountPostingLeg toEntity(AccountPostingLegRequest request) {
        if ( request == null ) {
            return null;
        }

        AccountPostingLeg.AccountPostingLegBuilder accountPostingLeg = AccountPostingLeg.builder();

        accountPostingLeg.legOrder( request.getLegOrder() );
        accountPostingLeg.targetSystem( request.getTargetSystem() );
        accountPostingLeg.account( request.getAccount() );
        accountPostingLeg.referenceId( request.getReferenceId() );
        accountPostingLeg.reason( request.getReason() );
        accountPostingLeg.postedTime( request.getPostedTime() );
        accountPostingLeg.requestPayload( request.getRequestPayload() );

        accountPostingLeg.attemptNumber( 1 );
        accountPostingLeg.status( resolveStatus(request) );
        accountPostingLeg.mode( resolveMode(request) );
        accountPostingLeg.operation( resolveOperation(request) );

        return accountPostingLeg.build();
    }

    @Override
    public AccountPostingLegResponse toResponse(AccountPostingLeg leg) {
        if ( leg == null ) {
            return null;
        }

        AccountPostingLegResponse accountPostingLegResponse = new AccountPostingLegResponse();

        accountPostingLegResponse.setPostingLegId( leg.getPostingLegId() );
        accountPostingLegResponse.setPostingId( leg.getPostingId() );
        accountPostingLegResponse.setLegOrder( leg.getLegOrder() );
        accountPostingLegResponse.setTargetSystem( leg.getTargetSystem() );
        accountPostingLegResponse.setAccount( leg.getAccount() );
        accountPostingLegResponse.setStatus( leg.getStatus() );
        accountPostingLegResponse.setReferenceId( leg.getReferenceId() );
        accountPostingLegResponse.setReason( leg.getReason() );
        accountPostingLegResponse.setAttemptNumber( leg.getAttemptNumber() );
        accountPostingLegResponse.setPostedTime( leg.getPostedTime() );
        accountPostingLegResponse.setMode( leg.getMode() );
        accountPostingLegResponse.setOperation( leg.getOperation() );
        accountPostingLegResponse.setCreatedAt( leg.getCreatedAt() );
        accountPostingLegResponse.setUpdatedAt( leg.getUpdatedAt() );

        return accountPostingLegResponse;
    }

    @Override
    public AccountPostingLegRequest toCreateLegRequest(AccountPostingRequest request, Integer legOrder, String targetSystem, LegMode mode, String operation, String requestPayload) {
        if ( request == null && legOrder == null && targetSystem == null && mode == null && operation == null && requestPayload == null ) {
            return null;
        }

        AccountPostingLegRequest accountPostingLegRequest = new AccountPostingLegRequest();

        if ( request != null ) {
            accountPostingLegRequest.setAccount( request.getDebtorAccount() );
        }
        accountPostingLegRequest.setLegOrder( legOrder );
        accountPostingLegRequest.setTargetSystem( targetSystem );
        accountPostingLegRequest.setMode( mode );
        accountPostingLegRequest.setOperation( operation );
        accountPostingLegRequest.setRequestPayload( requestPayload );

        return accountPostingLegRequest;
    }

    @Override
    public UpdateLegRequest toUpdateLegRequest(ExternalCallResult result) {
        if ( result == null ) {
            return null;
        }

        UpdateLegRequest updateLegRequest = new UpdateLegRequest();

        updateLegRequest.setStatus( result.status() );
        updateLegRequest.setReferenceId( result.referenceId() );
        updateLegRequest.setReason( result.reason() );
        updateLegRequest.setRequestPayload( result.requestPayload() );
        updateLegRequest.setResponsePayload( result.responsePayload() );
        updateLegRequest.setMode( result.mode() );

        updateLegRequest.setPostedTime( java.time.Instant.now() );

        return updateLegRequest;
    }

    @Override
    public void applyUpdate(UpdateLegRequest request, AccountPostingLeg leg) {
        if ( request == null ) {
            return;
        }

        if ( request.getStatus() != null ) {
            leg.setStatus( request.getStatus() );
        }
        if ( request.getReferenceId() != null ) {
            leg.setReferenceId( request.getReferenceId() );
        }
        if ( request.getReason() != null ) {
            leg.setReason( request.getReason() );
        }
        if ( request.getPostedTime() != null ) {
            leg.setPostedTime( request.getPostedTime() );
        }
        if ( request.getRequestPayload() != null ) {
            leg.setRequestPayload( request.getRequestPayload() );
        }
        if ( request.getResponsePayload() != null ) {
            leg.setResponsePayload( request.getResponsePayload() );
        }
        if ( request.getMode() != null ) {
            leg.setMode( request.getMode() );
        }
    }
}
