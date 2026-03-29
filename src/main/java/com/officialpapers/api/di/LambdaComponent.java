package com.officialpapers.api.di;

import com.officialpapers.api.handler.SampleDocumentHandler;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = LambdaModule.class)
public interface LambdaComponent {

    SampleDocumentHandler handler();
}
