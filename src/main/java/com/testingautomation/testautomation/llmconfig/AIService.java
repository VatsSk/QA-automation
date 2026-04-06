package com.testingautomation.testautomation.llmconfig;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Service;

import java.io.File;
import java.util.List;

@Service
public class AIService {
    public AIValidationResult analyzeScreenshots(String prompt, List<File> screenshots) {
        // 1. Convert images to Base64
        // 2. Build multimodal request:
        //    - text prompt
        //    - image1
        //    - image2
        //    - ...
        // 3. Call vision LLM
        // 4. Parse strict JSON response
        // 5. Return AIValidationResult

        throw new UnsupportedOperationException("Implement with your chosen LLM provider");
    }
}
