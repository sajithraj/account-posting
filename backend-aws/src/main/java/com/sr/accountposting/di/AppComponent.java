package com.sr.accountposting.di;

import com.sr.accountposting.handler.ApiGatewayHandler;
import com.sr.accountposting.handler.SqsHandler;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = AppModule.class)
public interface AppComponent {

    ApiGatewayHandler apiGatewayHandler();

    SqsHandler sqsHandler();
}
