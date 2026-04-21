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
