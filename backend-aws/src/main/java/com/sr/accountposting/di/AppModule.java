package com.sr.accountposting.di;

import com.sr.accountposting.infra.AwsClientFactory;
import com.sr.accountposting.service.leg.AccountPostingLegService;
import com.sr.accountposting.service.leg.AccountPostingLegServiceImpl;
import com.sr.accountposting.service.posting.AccountPostingService;
import com.sr.accountposting.service.posting.AccountPostingServiceImpl;
import com.sr.accountposting.service.processor.PostingProcessorService;
import com.sr.accountposting.service.processor.PostingProcessorServiceImpl;
import dagger.Binds;
import dagger.Module;
import dagger.Provides;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sqs.SqsClient;

import javax.inject.Singleton;

/**
 * Dagger module for the backend-aws Lambda.
 *
 * <p>Binds service interfaces to their implementations and provides AWS SDK clients:
 * <ul>
 *   <li>{@code SqsClient} — used by {@link com.sr.accountposting.service.posting.AccountPostingServiceImpl}
 *       to publish async jobs to {@code PROCESSING_QUEUE_URL}.</li>
 *   <li>{@code SnsClient} — used by {@link com.sr.accountposting.handler.SqsHandler} to publish
 *       failure alerts to {@code SUPPORT_ALERT_TOPIC_ARN}.</li>
 * </ul>
 * AWS clients are initialised via {@link com.sr.accountposting.infra.AwsClientFactory} which reads
 * the endpoint, region, and credentials from environment variables (supports LocalStack override).
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
    abstract PostingProcessorService bindPostingProcessorService(PostingProcessorServiceImpl impl);

    @Provides
    @Singleton
    static SqsClient provideSqsClient() {
        return AwsClientFactory.sqsClient();
    }

    @Provides
    @Singleton
    static SnsClient provideSnsClient() {
        return AwsClientFactory.snsClient();
    }
}
