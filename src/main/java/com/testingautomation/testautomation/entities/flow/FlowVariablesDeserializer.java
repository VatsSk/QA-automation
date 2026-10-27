package com.testingautomation.testautomation.entities.flow;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.core.JsonToken;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import com.testingautomation.testautomation.globalException.GlobalExceptionHandler;

import java.io.IOException;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * Custom Jackson deserializer for flow variables map.
 * Ensures duplicate variable names in incoming JSON objects cannot silently
 * overwrite earlier definitions during deserialization.
 */
public class FlowVariablesDeserializer extends JsonDeserializer<Map<String, FlowVariableDefinition>> {

    @Override
    public Map<String, FlowVariableDefinition> deserialize(JsonParser p, DeserializationContext ctxt)
            throws IOException {
        if (p.currentToken() == JsonToken.VALUE_NULL) {
            return new LinkedHashMap<>();
        }
        if (p.currentToken() != JsonToken.START_OBJECT) {
            throw new GlobalExceptionHandler.BadRequestException("Flow variables must be a JSON object.");
        }

        Map<String, FlowVariableDefinition> map = new LinkedHashMap<>();
        Set<String> seenLowerKeys = new HashSet<>();

        while (p.nextToken() != JsonToken.END_OBJECT) {
            String rawFieldName = p.currentName();
            p.nextToken(); // advance to value token

            if (rawFieldName == null || rawFieldName.trim().isEmpty()) {
                throw new GlobalExceptionHandler.BadRequestException("Flow variable name must not be blank.");
            }

            String trimmed = rawFieldName.trim();
            if (trimmed.startsWith("#")) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Flow variable name [" + rawFieldName + "] must not start with '#'. Stored names should not have the '#' prefix."
                );
            }

            String lower = trimmed.toLowerCase();
            if (seenLowerKeys.contains(lower)) {
                throw new GlobalExceptionHandler.BadRequestException(
                        "Duplicate flow variable name detected in JSON input: [" + trimmed +
                        "] conflicts with another variable (names are case-insensitive within a flow)."
                );
            }
            seenLowerKeys.add(lower);

            FlowVariableDefinition def = p.readValueAs(FlowVariableDefinition.class);
            map.put(trimmed, def);
        }

        return map;
    }
}
