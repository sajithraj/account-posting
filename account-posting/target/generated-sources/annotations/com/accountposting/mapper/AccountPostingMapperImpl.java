package com.accountposting.mapper;

import com.accountposting.dto.accountposting.AccountPostingCreateResponse;
import com.accountposting.dto.accountposting.AccountPostingRequest;
import com.accountposting.dto.accountposting.AccountPostingResponse;
import com.accountposting.dto.accountpostingleg.AccountPostingLegResponse;
import com.accountposting.dto.accountpostingleg.LegCreateResponse;
import com.accountposting.dto.accountpostingleg.LegResponse;
import com.accountposting.entity.AccountPosting;
import javax.annotation.processing.Generated;
import org.springframework.stereotype.Component;

@Generated(
    value = "org.mapstruct.ap.MappingProcessor",
    date = "2026-03-22T11:35:03+0530",
    comments = "version: 1.5.5.Final, compiler: javac, environment: Java 21.0.9 (Oracle Corporation)"
)
@Component
public class AccountPostingMapperImpl implements AccountPostingMapper {

    @Override
    public AccountPosting toEntity(AccountPostingRequest request) {
        if ( request == null ) {
            return null;
        }

        AccountPosting.AccountPostingBuilder accountPosting = AccountPosting.builder();

        accountPosting.sourceReferenceId( request.getSourceReferenceId() );
        accountPosting.endToEndReferenceId( request.getEndToEndReferenceId() );
        accountPosting.sourceName( request.getSourceName() );
        accountPosting.requestType( request.getRequestType() );
        accountPosting.amount( request.getAmount() );
        accountPosting.currency( request.getCurrency() );
        accountPosting.creditDebitIndicator( request.getCreditDebitIndicator() );
        accountPosting.debtorAccount( request.getDebtorAccount() );
        accountPosting.creditorAccount( request.getCreditorAccount() );
        accountPosting.requestedExecutionDate( request.getRequestedExecutionDate() );
        accountPosting.remittanceInformation( request.getRemittanceInformation() );

        return accountPosting.build();
    }

    @Override
    public AccountPostingResponse toResponse(AccountPosting posting) {
        if ( posting == null ) {
            return null;
        }

        AccountPostingResponse.AccountPostingResponseBuilder accountPostingResponse = AccountPostingResponse.builder();

        accountPostingResponse.postingStatus( posting.getStatus() );
        accountPostingResponse.postingId( posting.getPostingId() );
        accountPostingResponse.sourceReferenceId( posting.getSourceReferenceId() );
        accountPostingResponse.endToEndReferenceId( posting.getEndToEndReferenceId() );
        accountPostingResponse.sourceName( posting.getSourceName() );
        accountPostingResponse.requestType( posting.getRequestType() );
        accountPostingResponse.amount( posting.getAmount() );
        accountPostingResponse.currency( posting.getCurrency() );
        if ( posting.getCreditDebitIndicator() != null ) {
            accountPostingResponse.creditDebitIndicator( posting.getCreditDebitIndicator().name() );
        }
        accountPostingResponse.debtorAccount( posting.getDebtorAccount() );
        accountPostingResponse.creditorAccount( posting.getCreditorAccount() );
        accountPostingResponse.requestedExecutionDate( posting.getRequestedExecutionDate() );
        accountPostingResponse.remittanceInformation( posting.getRemittanceInformation() );
        accountPostingResponse.targetSystems( posting.getTargetSystems() );
        accountPostingResponse.reason( posting.getReason() );
        accountPostingResponse.createdAt( posting.getCreatedAt() );
        accountPostingResponse.updatedAt( posting.getUpdatedAt() );

        return accountPostingResponse.build();
    }

    @Override
    public LegResponse toLegResponse(AccountPostingLegResponse legResponse) {
        if ( legResponse == null ) {
            return null;
        }

        LegResponse legResponse1 = new LegResponse();

        legResponse1.setPostingLegId( legResponse.getPostingLegId() );
        legResponse1.setName( legResponse.getTargetSystem() );
        legResponse1.setType( legResponse.getOperation() );
        legResponse1.setLegOrder( legResponse.getLegOrder() );
        if ( legResponse.getStatus() != null ) {
            legResponse1.setStatus( legResponse.getStatus().name() );
        }
        if ( legResponse.getMode() != null ) {
            legResponse1.setMode( legResponse.getMode().name() );
        }
        legResponse1.setAccount( legResponse.getAccount() );
        legResponse1.setReferenceId( legResponse.getReferenceId() );
        legResponse1.setPostedTime( legResponse.getPostedTime() );
        legResponse1.setReason( legResponse.getReason() );

        return legResponse1;
    }

    @Override
    public AccountPostingCreateResponse toCreateResponse(AccountPosting posting) {
        if ( posting == null ) {
            return null;
        }

        AccountPostingCreateResponse.AccountPostingCreateResponseBuilder accountPostingCreateResponse = AccountPostingCreateResponse.builder();

        accountPostingCreateResponse.postingStatus( posting.getStatus() );
        accountPostingCreateResponse.sourceReferenceId( posting.getSourceReferenceId() );
        accountPostingCreateResponse.endToEndReferenceId( posting.getEndToEndReferenceId() );

        return accountPostingCreateResponse.build();
    }

    @Override
    public LegCreateResponse toLegCreateResponse(AccountPostingLegResponse legResponse) {
        if ( legResponse == null ) {
            return null;
        }

        LegCreateResponse legCreateResponse = new LegCreateResponse();

        legCreateResponse.setName( legResponse.getTargetSystem() );
        legCreateResponse.setType( legResponse.getOperation() );
        legCreateResponse.setAccount( legResponse.getAccount() );
        legCreateResponse.setReferenceId( legResponse.getReferenceId() );
        legCreateResponse.setPostedTime( legResponse.getPostedTime() );
        if ( legResponse.getStatus() != null ) {
            legCreateResponse.setStatus( legResponse.getStatus().name() );
        }
        legCreateResponse.setReason( legResponse.getReason() );

        return legCreateResponse;
    }
}
