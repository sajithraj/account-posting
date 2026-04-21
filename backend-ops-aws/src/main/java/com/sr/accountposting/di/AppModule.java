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
