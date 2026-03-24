/**
 * config.js — Scenario type definitions.
 *
 * Field rules (NO "statement" field on any scenario):
 *
 *   URL        → url field  +  test cases (CSV or manual)
 *   MODAL      → cssSelector  +  test cases (CSV or manual)
 *   URL_NAV    → url field only           (no test data)
 *   MODAL_NAV  → cssSelector only         (no test data)
 *   SEARCH_NAV → cssSelector + value      (no test data)
 *   VERIFY_PAGE→ url + cssSelector        (no test data)
 */

export const TYPES = {
    URL: {
        label:   'URL',
        icon:    '🔗',
        color:   'var(--blue)',
        bg:      'var(--blue-d)',
        // Fields shown in the editor for this type
        fields:  ['url'],
        // Whether this type supports test data (CSV or manual rows)
        hasData: true,
        hint:    'Navigate to a URL and run test cases',
    },
    MODAL: {
        label:   'Modal',
        icon:    '◫',
        color:   'var(--pu)',
        bg:      'var(--pu-d)',
        fields:  ['cssSelector'],
        hasData: true,
        hint:    'Open a modal by CSS selector and run test cases',
    },
    URL_NAV: {
        label:   'URL Nav',
        icon:    '→',
        color:   'var(--te)',
        bg:      'var(--te-d)',
        fields:  ['url'],
        hasData: false,
        hint:    'Navigate to a URL — no test data needed',
    },
    MODAL_NAV: {
        label:   'Modal Nav',
        icon:    '⇌',
        color:   'var(--am)',
        bg:      'var(--am-d)',
        fields:  ['cssSelector'],
        hasData: false,
        hint:    'Open a modal by CSS selector — no test data needed',
    },
    SEARCH_NAV: {
        label:   'Search Nav',
        icon:    '⌕',
        color:   'var(--gr)',
        bg:      'var(--gr-d)',
        fields:  ['cssSelector', 'value'],
        hasData: false,
        hint:    'Navigate using a search field — enter selector and search term',
    },
    VERIFY_PAGE: {
        label:   'Verify Page',
        icon:    '✓',
        color:   'var(--gr)',
        bg:      'var(--gr-d)',
        fields:  ['url', 'cssSelector'],
        hasData: false,
        hint:    'Verify page content by URL and CSS selector',
    },
    ASSERT: {
        label:   'Assert',
        icon:    '✓',
        color:   'var(--or)',
        bg:      'var(--or-d)',
        fields:  ['assertType', 'tableSelector', 'cssSelector', 'expectedValue', 'columnName', 'order', 'btnSelector'],
        hasData: false,
        hint:    'Assert element value or text content',
        dynamicFields: true,
    },
};

// Assertion type definitions and their field requirements
export const ASSERT_TYPES = {
    // 🔹 Basic UI
    ASSERT_VISIBLE: {
        label: 'Element Visible',
        fields: ['cssSelector'],
        required: ['cssSelector']
    },
    ASSERT_NOT_VISIBLE: {
        label: 'Element Not Visible',
        fields: ['cssSelector'],
        required: ['cssSelector']
    },
    ASSERT_ELEMENT_PRESENT: {
        label: 'Element Present',
        fields: ['cssSelector'],
        required: ['cssSelector']
    },

    // 🔹 Text / Value
    ASSERT_TEXT_EQUALS: {
        label: 'Text Equals',
        fields: ['cssSelector', 'expectedValue'],
        required: ['cssSelector', 'expectedValue']
    },
    ASSERT_TEXT_CONTAINS: {
        label: 'Text Contains',
        fields: ['cssSelector', 'expectedValue'],
        required: ['cssSelector', 'expectedValue']
    },

    // 🔹 Grid / Table
    ASSERT_COLUMN_PRESENT: {
        label: 'Column(s) Present',
        fields: ['tableSelector', 'columnName'],
        required: ['tableSelector', 'columnName']
    },
    ASSERT_COUNT: {
        label: 'Count',
        fields: ['tableSelector', 'btnSelector'],
        required: ['tableSelector']
    },

    // 🔹 Behavior
    ASSERT_SORT_ORDER: {
        label: 'Sort Order',
        fields: ['tableSelector', 'columnName', 'order'],
        required: ['tableSelector', 'columnName', 'order']
    },
    ASSERT_PAGINATION: {
        label: 'Pagination',
        fields: ['tableSelector', 'btnSelector'],
        required: ['tableSelector']
    },

    // 🔹 Advanced
    ASSERT_API_CALLED: {
        label: 'API Called',
        fields: ['cssSelector'],
        required: ['cssSelector']
    },
    ASSERT_ATTRIBUTE: {
        label: 'Attribute',
        fields: ['cssSelector', 'expectedValue'],
        required: ['cssSelector', 'expectedValue']
    }
};

// Sort order options
export const SORT_ORDER_OPTIONS = [
    { value: 'ascending', label: 'Ascending' },
    { value: 'descending', label: 'Descending' }
];

// Minimum required fields per type (used for validation before save)
export const REQUIRED = {
    URL:        ['url'],
    MODAL:      ['cssSelector'],
    URL_NAV:    ['url'],
    MODAL_NAV:  ['cssSelector'],
    SEARCH_NAV: ['cssSelector', 'value'],
    VERIFY_PAGE: ['url', 'cssSelector'],
    ASSERT:     [],
};
