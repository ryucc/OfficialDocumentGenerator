package com.officialpapers.api.service;

import software.amazon.awssdk.services.lambda.LambdaClient;
import software.amazon.awssdk.services.lambda.model.InvocationType;
import software.amazon.awssdk.services.lambda.model.InvokeRequest;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

@Singleton
public class LambdaInstructionRecompileTrigger implements InstructionRecompileTrigger {

    private final LambdaClient lambdaClient;
    private final String functionName;

    @Inject
    public LambdaInstructionRecompileTrigger(
            LambdaClient lambdaClient,
            @Named("rulesCompilerFunctionName") String functionName
    ) {
        this.lambdaClient = lambdaClient;
        this.functionName = functionName;
    }

    @Override
    public void requestRecompile() {
        try {
            lambdaClient.invoke(InvokeRequest.builder()
                    .functionName(functionName)
                    .invocationType(InvocationType.EVENT)  // Async invocation
                    .payload("{}")
                    .build());
        } catch (RuntimeException exception) {
            // Log but don't propagate - recompilation is a background task
            System.err.println("Failed to trigger rules recompilation: " + exception.getMessage());
        }
    }
}
