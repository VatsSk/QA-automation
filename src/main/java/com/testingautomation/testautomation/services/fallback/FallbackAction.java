package com.testingautomation.testautomation.services.fallback;

@FunctionalInterface
public interface FallbackAction<T> {

    T execute() throws Exception;
}
