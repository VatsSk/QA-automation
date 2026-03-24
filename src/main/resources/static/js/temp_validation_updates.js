function ensureAssertions(scenario) {
    if (!scenario.assertions) {
        scenario.assertions = [];
    }
    return scenario.assertions;
}
function validate() {
    const name = document.getElementById('f-runName').value.trim();
    if (!name) { toast('Run name is required', 'warning'); return false; }
    if (!scenarios.length) { toast('Add at least one scenario', 'warning'); return false; }

    for (let i = 0; i < scenarios.length; i++) {
        const s   = scenarios[i];
        const req = REQUIRED[s.type] || [];

        // 🔹 NORMAL TYPES
        // if (s.type !== 'ASSERT') {
        //     for (const f of req) {
        //         const val = f === 'cssSelector' ? s.cssSelector : s[f];
        //         if (!val?.trim()) {
        //             toast(`Scenario #${i + 1} (${s.type}): "${f}" is required`, 'warning');
        //             selectScenario(i);
        //             return false;
        //         }
        //     }
        // }

        // 🔥 ASSERT TYPE VALIDATION
        if (s.type === 'ASSERT') {
            const assertions = ensureAssertions(s);

            if (assertions.length === 0) {
                toast(`Scenario #${i + 1} (ASSERT): At least one assertion is required`, 'warning');
                selectScenario(i);
                return false;
            }

            for (let ai = 0; ai < assertions.length; ai++) {
                const assertion = assertions[ai];

                if (!assertion.assertType) {
                    toast(`Scenario #${i + 1} (ASSERT): Assertion #${ai + 1} requires a type`, 'warning');
                    selectScenario(i);
                    return false;
                }

                const assertConfig = ASSERT_TYPES[assertion.assertType] || { required: [] };

                for (const field of assertConfig.required) {
                    if (!assertion[field]?.trim()) {
                        toast(`Scenario #${i + 1} (ASSERT): Assertion #${ai + 1} requires "${field}"`, 'warning');
                        selectScenario(i);
                        return false;
                    }
                }
            }
        }
    }

    return true;
}