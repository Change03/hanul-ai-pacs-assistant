package com.hanul.aipacs.ai.provider;

import com.hanul.aipacs.ai.domain.ProcessedDicom;
import com.hanul.aipacs.ai.domain.ProviderResult;

public interface InferenceProvider {
    String name();

    ProviderResult infer(ProcessedDicom processedDicom);
}
