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
        fields:  [],
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
    FORM_MODAL: {
        label:   'Form Modal',
        icon:    '📝',
        color:   'var(--purple)',
        bg:      'var(--purple-d)',
        fields:  ['cssSelector', 'value', 'clickCss'],
        hasData: true,
        hint:    'Fill form field and optionally click after filling',
    },
    ASSERT: {
        label:   'Assert',
        icon:    '✓',
        color:   'var(--or)',
        bg:      'var(--or-d)',
        fields:  ['type', 'tableId', 'locator', 'expected', 'columnName', 'rangeId', 'rowsBtn'],
        hasData: false,
        hint:    'Assert element value or text content',
        dynamicFields: true,
    },
    FILTER_NAV: {
        label:   'Filter Nav',
        icon:    '⛃',
        color:   'var(--cyan)',
        bg:      'var(--cyan-d)',
        // Top-level fields
        fields:  ['filters', 'applyBtnCss'],
        hasData: false,
        hint: 'Apply multiple filters and trigger search',
        dynamicFields: true,
    },
    DATE_RANGE_NAV: {
        label:   'Date Range Nav',
        icon:    '📅',
        color:   'var(--pink)',
        bg:      'var(--pink-d)',

        fields: [
            'inputSelector',
            'selectionType',

            // preset mode
            'preset',

            // custom mode
            'startDate',
            'endDate',

            'applyButtonSelector',
            'calendarContainerSelector',
            'dateFormat'
        ],

        hasData: false,

        hint: 'Select date range using preset or custom dates',

        dynamicFields: true,
    },
    MANAGE_COL_NAV: {
        label: 'Manage Columns',
        icon: '📑',
        color: 'var(--indigo)',
        bg: 'var(--indigo-d)',

        fields: [
            'columns',
            'saveBtnCss'
        ],

        hasData: false,

        hint: 'Show, hide and reorder table columns',

        dynamicFields: true,
    },
    ROW_COUNT_NAV: {
        label: 'Row Count',
        icon: '📊',
        color: 'var(--pri)',
        bg: 'var(--pri-bg)',
        fields: [
            'cssSelector',
            'value'
        ],
        hasData: false,
        hint: 'Change rows count on table'
    }

};

// Assertion type definitions and their field requirements
export const ASSERT_TYPES = {
    // 🔹 Basic UI
    ASSERT_VISIBLE: {
        label: 'Element Visible',
        fields: ['locator'],
        required: ['locator']
    },
    ASSERT_NOT_VISIBLE: {
        label: 'Element Not Visible',
        fields: ['locator'],
        required: ['locator']
    },
    ASSERT_ELEMENT_PRESENT: {
        label: 'Element Present',
        fields: ['locator'],
        required: ['locator']
    },

    // 🔹 Text / Value
    ASSERT_TEXT_EQUALS: {
        label: 'Assert Column Value(s)',
        fields: ['tableId', 'columnName','expected'],
        required: ['tableId', 'columnName','expected']
    },
    ASSERT_TEXT_CONTAINS: {
        label: 'Text Contains',
        fields: ['locator', 'expected'],
        required: ['locator', 'expected']
    },

    // 🔹 Grid / Table
    ASSERT_COLUMN_PRESENT: {
        label: 'Column(s) Present',
        fields: ['tableId', 'columnName'],
        required: ['tableId', 'columnName']
    },
    ASSERT_COUNT: {
        label: 'Pagination',
        fields: ['tableId', 'rowsBtn'],
        required: ['tableId','rowsBtn']
    },

    // 🔹 Behavior
    ASSERT_SORT_ORDER: {
        label: 'Sort Order',
        fields: ['tableId', 'columnName', 'order'],
        required: ['tableId', 'columnName', 'order']
    },
    // ASSERT_PAGINATION: {
    //     label: 'Pagination',
    //     fields: ['tableId', 'rowsBtn'],
    //     required: ['tableId']
    // },

    // 🔹 Advanced
    ASSERT_API_CALLED: {
        label: 'API Called',
        fields: ['locator'],
        required: ['locator']
    },
    ASSERT_ATTRIBUTE: {
        label: 'Attribute',
        fields: ['locator', 'expected'],
        required: ['locator', 'expected']
    },
    ASSERT_AI:{
        label:'Assert With AI',
        fields:['promptAi'],
        required:['promptAi']
    },
    ASSERT_FILTER:{
        label :'Assert Table filter',
        fields : ['tableId'],
        required:['tableId']
    },
    ASSERT_MANAGE_COLUMN:{
        label:'Assert Manage Column',
        fields:['tableId'],
        required:['tableId']
    },
    ASSERT_ROWS_COUNT:{
        label :'Assert table rows',
        fields: ['tableId'],
        required: ['tableId']
    }


};
export const MANAGE_COLUMN_ACTIONS = [
    { value: '', label: 'Default' },   // null action
    { value: 'SHOW', label: 'Show' },
    { value: 'HIDE', label: 'Hide' }
];
export const FILTER_TYPES = [
    { value: 'TEXT', label: 'Text' },
    { value: 'DATE', label: 'Date' },
    { value: 'DATE_TIME', label: 'Date Time' },
    { value: 'NUMBER', label: 'Number' },
];

export const   FILTER_OPERATIONS = {
    TEXT: [
        { value: 'EQUALS', label: 'Equals' },
        { value: 'NOT_EQUALS', label: 'Not Equals' },
        { value: 'CONTAINS', label: 'Contains' },
        { value: 'STARTS_WITH', label: 'Starts With' },
    ],
    DATE: [
        { value: 'EQUALS', label: 'Equals' },
        { value: 'GREATER_THAN', label: 'Greater Than' },
        { value: 'LESS_THAN', label: 'Less Than' },
        { value: 'RANGE', label: 'Date Range' },
    ],
    DATE_TIME: [
        { value: 'EQUALS', label: 'Equals' },
        { value: 'GREATER_THAN', label: 'Greater Than' },
        { value: 'LESS_THAN', label: 'Less Than' },
        { value: 'RANGE', label: 'Date Range' },
    ],

    NUMBER: [
        { value: 'EQUALS', label: 'Equals' },
        { value: 'GREATER_THAN', label: 'Greater Than' },
        { value: 'LESS_THAN', label: 'Less Than' },
    ]
};


// Sort order options
export const SORT_ORDER_OPTIONS = [
    { value: 'ascending', label: 'Ascending' },
    { value: 'descending', label: 'Descending' }
];

export const DATE_PRESET_TYPES = [
    { value: 'TODAY', label: 'Today' },
    { value: 'YESTERDAY', label: 'Yesterday' },

    // { value: 'THIS_WEEK', label: 'This Week' },
    // { value: 'LAST_WEEK', label: 'Last Week' },

    { value: 'THIS_MONTH', label: 'This Month' },
    { value: 'LAST_MONTH', label: 'Last Month' },

    // { value: 'CUSTOM_RANGE', label: 'Custom Range' },
];

export const DATE_SELECTION_TYPES = [
    { value: 'PRESET', label: 'Preset' },
    { value: 'CUSTOM', label: 'Custom Range' }
];

// Minimum required fields per type (used for validation before save)
export const REQUIRED = {
    URL:        ['url'],
    MODAL:      [],
    URL_NAV:    ['url'],
    MODAL_NAV:  ['cssSelector'],
    SEARCH_NAV: ['cssSelector', 'value'],
    VERIFY_PAGE: ['url', 'cssSelector'],
    FORM_MODAL:  [],
    ASSERT:     [],
    FILTER_NAV: ['applyBtnCss'],
    DATE_RANGE_NAV: ['inputSelector', 'selectionType', 'applyButtonSelector','calendarContainerSelector'],
    MANAGE_COL_NAV: ['saveBtnCss'],
};
