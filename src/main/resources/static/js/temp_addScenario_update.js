window.addScenario = function (type) {
    const newScenario = {
        _key:            ++scKey,
        id:              undefined,
        type,
        url:             '',
        cssSelector:     '',
        value:           '',
        csv:             '',
        manualTestCases: [],
    };

    // 🔥 ONLY ADD THIS (no breaking change)
    if (type === 'ASSERT') {
        newScenario.assertions = [];
    }

    scenarios.push(newScenario);
    scIdx = scenarios.length - 1;
    closeModal();
    renderSidebar();
    renderPanel();
};