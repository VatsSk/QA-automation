package com.testingautomation.testautomation.enums;
/**
 * Allowed scenario types.
 * NOTE: RESULT_STATEMENT is NOT a scenario type.
 * The resultStatement lives on the Run document and is passed
 * as a query param (?resultStatement=...) to POST /runner/run-auth.
 */
public enum ScenarioType {
    URL,
    MODAL,
    URL_NAV,
    MODAL_NAV,
    SEARCH_NAV,
    FORM_MODAL,
    ASSERT,
    VERIFY_PAGE,
    AI_ASSERT,
    FILTER_NAV,
    DATE_RANGE_NAV,
    MANAGE_COL_NAV
}
