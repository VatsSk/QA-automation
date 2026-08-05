package com.testingautomation.testautomation.utils;

import com.testingautomation.testautomation.entities.ProjectEnvironment;
import com.testingautomation.testautomation.enums.flow.ActionType;
import com.testingautomation.testautomation.entities.flow.FlowStep;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.URISyntaxException;

/**
 * Resolves the URL of a NAVIGATE step at execution time.
 *
 * Responsibilities:
 *  - For NAVIGATE steps: replaces the origin (protocol+host+port) with the
 *    selected environment's baseUrl, preserving path, query and fragment.
 *  - For all other steps: returns the step's value unchanged.
 *  - If no environment is provided: returns the step's value unchanged
 *    (backward compatible — existing flows execute exactly as before).
 *
 * This component never reads from or writes to MongoDB.
 * It operates purely in-memory during the execution pipeline.
 */
@Component
public class NavigationUrlResolver {

    private static final Logger logger = LoggerFactory.getLogger(NavigationUrlResolver.class);

    /**
     * Returns the URL that Selenium should navigate to for this step.
     *
     * @param step        The original recorded FlowStep (never modified)
     * @param environment The selected environment, or null if none selected
     * @return            The resolved URL string ready for driver.get()
     */
    public String resolve(FlowStep step, ProjectEnvironment environment) {
        // Only NAVIGATE steps are affected
        if (step.getActionType() != ActionType.NAVIGATE) {
            return step.getValue();
        }

        // No environment selected → use recorded URL as-is (backward compatible)
        if (environment == null || environment.getBaseUrl() == null || environment.getBaseUrl().isBlank()) {
            return step.getValue();
        }

        String recordedUrl = step.getValue();
        if (recordedUrl == null || recordedUrl.isBlank()) {
            return recordedUrl;
        }

        return resolveOrigin(recordedUrl, environment.getBaseUrl(), step);
    }

    /**
     * Core resolution logic using java.net.URI — never String.replace().
     *
     * Extracts only the path, query, and fragment from the recorded URL.
     * Prepends the environment's origin (scheme + authority).
     *
     * Example:
     *   recorded:     https://tfqa.trackofarm.in/login?lang=en
     *   environment:  https://dev.trackofarm.in
     *   result:       https://dev.trackofarm.in/login?lang=en
     */
    private String resolveOrigin(String recordedUrl, String environmentBaseUrl, FlowStep step) {
        try {
            URI recorded = new URI(recordedUrl);
            URI base = new URI(environmentBaseUrl);

            URI resolved = new URI(
                    base.getScheme(),       // protocol from environment
                    base.getAuthority(),    // host + port from environment
                    recorded.getPath(),     // path preserved from recorded URL
                    recorded.getQuery(),    // query params preserved from recorded URL
                    recorded.getFragment()  // fragment preserved from recorded URL
            );

            String resolvedUrl = resolved.toString();
            logger.info(
                "NavigationUrlResolver: Step [{}] | Recorded: {} | Resolved: {}",
                step.getName(), recordedUrl, resolvedUrl
            );
            return resolvedUrl;

        } catch (URISyntaxException e) {
            // If the recorded URL is malformed, fall back to the recorded URL.
            // Log a warning but never crash the execution over a URL parse issue.
            logger.warn(
                "NavigationUrlResolver: Could not resolve URL '{}' with base '{}' for step [{}]. " +
                "Falling back to recorded URL. Error: {}",
                recordedUrl, environmentBaseUrl, step.getName(), e.getMessage()
            );
            return recordedUrl;
        }
    }
}
