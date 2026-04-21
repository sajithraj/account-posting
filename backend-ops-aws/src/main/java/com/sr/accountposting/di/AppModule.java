package com.sr.accountposting.di;

import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.service.config.PostingConfigService;
import com.sr.accountposting.service.config.PostingConfigServiceImpl;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.leg.AccountPostingLegServiceImpl;
import com.sr.accountposting.service.posting.AccountPostingService;
import com.sr.accountposting.service.posting.AccountPostingServiceImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.inject.Singleton;

/**
 * Dagger module for the backend-ops-aws Lambda.
 *
 * <p>Binds service interfaces to their implementations and provides AWS SDK clients:
 * <ul>
 *   <li>{@code SqsClient} — used by {@link com.sr.accountposting.service.posting.AccountPostingServiceImpl}
 *       to publish retry jobs to {@code PROCESSING_QUEUE_URL}.</li>
 * </ul>
 * No {@code SnsClient} is needed here — this Lambda does not publish failure alerts.
 * AWS clients are initialised via {@link com.sr.accountposting.infra.AwsClientFactory}.
 */
@Module
public abstract class AppModule {

    @Binds
    @Singleton
    abstract AccountPostingService bindAccountPostingService(AccountPostingServiceImpl impl);

    @Binds
    @Singleton
    abstract AccountPostingLegService bindAccountPostingLegService(AccountPostingLegServiceImpl impl);

    @Binds
    @Singleton
    abstract PostingConfigService bindPostingConfigService(PostingConfigServiceImpl impl);

    @Provides
    @Singleton
    static SqsClient provideSqsClient() {
        return AwsClientFactory.sqsClient();
    }
}
