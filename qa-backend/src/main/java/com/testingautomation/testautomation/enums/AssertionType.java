package com.testingautomation.testautomation.enums;

public enum AssertionType {

    // 🔹 Basic UI
    ASSERT_VISIBLE,
    ASSERT_NOT_VISIBLE,
    ASSERT_ELEMENT_PRESENT,

    // 🔹 Text / Value
    ASSERT_TEXT_EQUALS,
    ASSERT_TEXT_CONTAINS,

    // 🔹 Grid / Table
    ASSERT_COLUMN_PRESENT,
    ASSERT_COUNT,

    // 🔹 Behavior
    ASSERT_SORT_ORDER,
    ASSERT_PAGINATION,

    // 🔹 Advanced
    ASSERT_API_CALLED,
    ASSERT_ATTRIBUTE,
    ASSERT_AI,
    ASSERT_FILTER,
    ASSERT_MANAGE_COLUMN,
    ASSERT_ROWS_COUNT,
}