package com.officialpapers.api.di;

import com.officialpapers.api.handler.DocumentInstructionHandler;
import dagger.Component;

import javax.inject.Singleton;

@Singleton
@Component(modules = LambdaModule.class)
public interface LambdaComponent {

    DocumentInstructionHandler handler();
}
