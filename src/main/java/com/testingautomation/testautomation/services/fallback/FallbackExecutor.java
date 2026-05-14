package com.testingautomation.testautomation.services.fallback;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

public class FallbackExecutor {

    private static final Logger logger =
            LoggerFactory.getLogger(FallbackExecutor.class);

    public static <T> T execute(
            List<String> names,
            List<FallbackAction<T>> actions
    ) {

        if (names.size() != actions.size()) {

            throw new IllegalArgumentException(
                    "Fallback names and actions size mismatch"
            );
        }

        Exception lastException = null;

        for (int i = 0; i < actions.size(); i++) {

            String strategyName = names.get(i);

            logger.info(
                    "Trying fallback strategy: {}",
                    strategyName
            );

            try {

                T result = actions.get(i).execute();

                logger.info(
                        "Fallback SUCCESS: {}",
                        strategyName
                );

                return result;

            }
            catch (Exception e) {

                lastException = e;

                logger.warn(
                        "Fallback FAILED: {} reason={}",
                        strategyName,
                        e.getMessage()
                );
            }
        }

        logger.error("All fallback strategies failed");

        throw new RuntimeException(
                "All fallback strategies failed",
                lastException
        );
    }
}