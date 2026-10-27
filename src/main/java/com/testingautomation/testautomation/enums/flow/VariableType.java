package com.testingautomation.testautomation.enums.flow;

/**
 * Logical type of a flow variable.
 * Currently STRING and NUMBER are supported; both are stored as Strings
 * during execution to preserve leading zeros.
 */
public enum VariableType {
    STRING,
    NUMBER
}
