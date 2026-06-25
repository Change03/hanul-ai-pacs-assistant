package com.hanul.aipacs.ai.provider;

public class ProviderResolver {
    private final InferenceProvider current;

    public ProviderResolver(InferenceProvider current) {
        this.current = current;
    }

    public InferenceProvider current() {
        return current;
    }
}
