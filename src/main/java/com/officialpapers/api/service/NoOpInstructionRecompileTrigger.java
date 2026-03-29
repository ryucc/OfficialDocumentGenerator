package com.officialpapers.api.service;

import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
public class NoOpInstructionRecompileTrigger implements InstructionRecompileTrigger {

    @Inject
    public NoOpInstructionRecompileTrigger() {
    }

    @Override
    public void requestRecompile() {
        // Placeholder for future async instruction compilation.
    }
}
