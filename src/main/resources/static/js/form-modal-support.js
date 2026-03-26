// // Helper function to add FORM_MODAL support
// export function addFormModalSupport() {
//
//     // Add clickCss field to scenario object initialization
//     window.addScenario = function (type) {
//         const newScenario = {
//             _key:            ++scKey,
//             id:              undefined,
//             type,
//             url:             '',
//             cssSelector:     '',
//             value:           '',
//             clickCss:        '', // Add clickCss field
//             csv:             '',
//             manualTestCases: [],
//         };
//
//         // 🔥 ONLY ADD THIS (no breaking change)
//         if (type === 'ASSERT') {
//             newScenario.assertions = [];
//         }
//
//         console.log(type+"choosen!")
//
//         scenarios.push(newScenario);
//         scIdx = scenarios.length - 1;
//         closeModal();
//         renderSidebar();
//         renderPanel();
//     };
//
//     // Add click option handler function
//     window.handleClickOption = function (scIdx, option) {
//         const scenario = scenarios[scIdx];
//         if (option === 'yes') {
//             scenario.clickCss = scenario.clickCss || '';
//         } else {
//             scenario.clickCss = '';
//         }
//         renderPanel();
//     };
//
//     // Update renderPanel to include FORM_MODAL specific fields
//     const originalRenderPanel = window.renderPanel;
//     window.renderPanel = function() {
//         const panel = document.getElementById('sc-panel');
//         if (!scenarios.length) {
//             panel.innerHTML = `
//         <div class="empty">
//           <div class="ei">🔬</div>
//           <h3>No scenarios yet</h3>
//           <p>Click "+ Add Scenario" in left panel to get started.</p>
//         </div>`;
//             return;
//         }
//
//         const s   = scenarios[scIdx];
//         const cfg = TYPES[s.type] || { label: s.type, icon: '?', color: 'var(--tx2)', bg: 'var(--sur3)', fields: [], hasData: false };
//         const tab = scTab[s._key] || 'manual';
//
//         // Type picker buttons
//         const typePicker = Object.entries(TYPES).map(([k, v]) => `
//       <button class="tp-btn ${s.type === k ? 'active' : ''}"
//         onclick="setType(${scIdx}, '${k}')"
//         style="--tc:${v.color};--tb:${v.bg}">
//         <span class="tp-icon">${v.icon}</span>
//         <span class="tp-lbl">${v.label}</span>
//       </button>`).join('');
//
//         panel.innerHTML = `
//     <div class="card">
//       <div class="card-header"
//         style="background:${cfg.bg};border-color:${cfg.color.replace(')', ',0.25)')}">
//         <span class="card-title" style="color:${cfg.color}">
//           ${cfg.icon} Scenario #${scIdx + 1} — ${cfg.label}
//         </span>
//         <div style="display:flex;gap:6px">
//           <button class="btn btn-ghost btn-xs"
//             onclick="moveScenario(${scIdx}, -1)"
//             ${scIdx === 0 ? 'disabled' : ''} title="Move up">↑</button>
//           <button class="btn btn-ghost btn-xs"
//             onclick="moveScenario(${scIdx}, 1)"
//             ${scIdx === scenarios.length - 1 ? 'disabled' : ''} title="Move down">↓</button>
//         </div>
//       </div>
//       <div class="card-body">
//
//         <!-- Type picker -->
//         <div class="sc-section">Scenario Type</div>
//         <div class="type-picker">${typePicker}</div>
//
//         <div style="padding:8px 12px;background:var(--sur2);border:1px solid var(--bd);
//           border-radius:var(--rs);font-size:12px;color:var(--tx2);margin-bottom:16px">
//           ℹ ${esc(cfg.hint || '')}
//         </div>
//
//         <!-- ── URL field (shown for URL, URL_NAV, VERIFY_PAGE) ── -->
//         ${cfg.fields.includes('url') ? `
//         <div class="field">
//           <label class="lbl">URL *</label>
//           <input class="inp inp-mono" id="sc-url"
//             value="${esc(s.url)}"
//             placeholder="https://example.com/page"
//             oninput="updateField(${scIdx}, 'url', this.value)">
//         </div>` : ''}
//
//         <!-- ── CSS Selector (shown for MODAL, MODAL_NAV, SEARCH_NAV, FORM_MODAL) ── -->
//         ${cfg.fields.includes('cssSelector') ? `
//         <div class="field">
//           <label class="lbl">CSS Selector *</label>
//           <input class="inp inp-mono" id="sc-css"
//             value="${esc(s.cssSelector)}"
//             placeholder=".modal-trigger, #open-btn"
//             oninput="updateField(${scIdx}, 'cssSelector', this.value)">
//         </div>` : ''}
//
//         <!-- ── Value (for SEARCH_NAV and FORM_MODAL) ── -->
//         ${cfg.fields.includes('value') ? `
//         <div class="field">
//           <label class="lbl">${s.type === 'FORM_MODAL' ? 'Form Value *' : 'Search Value *'}</label>
//           <input class="inp" id="sc-value"
//             value="${esc(s.value)}"
//             placeholder="${s.type === 'FORM_MODAL' ? 'Value to type in form field' : 'Search term or input text'}"
//             oninput="updateField(${scIdx}, 'value', this.value)">
//         </div>` : ''}
//
//         <!-- ── Click Option (only for FORM_MODAL) ── -->
//         ${s.type === 'FORM_MODAL' ? `
//         <div class="field">
//           <label class="lbl">Click After Filling?</label>
//           <div style="display:flex;gap:16px;margin-top:8px">
//             <label style="display:flex;align-items:center;gap:6px;cursor:pointer">
//               <input type="radio" name="click-option-${scIdx}" value="yes"
//                 ${s.clickCss ? 'checked' : ''}
//                 onchange="handleClickOption(${scIdx}, 'yes')">
//               <span>Yes</span>
//             </label>
//             <label style="display:flex;align-items:center;gap:6px;cursor:pointer">
//               <input type="radio" name="click-option-${scIdx}" value="no"
//                 ${!s.clickCss ? 'checked' : ''}
//                 onchange="handleClickOption(${scIdx}, 'no')">
//               <span>No</span>
//             </label>
//           </div>
//         </div>` : ''}
//
//         <!-- ── Click CSS (only for FORM_MODAL when click option is yes) ── -->
//         ${s.type === 'FORM_MODAL' && s.clickCss ? `
//         <div class="field">
//           <label class="lbl">Click CSS Selector *</label>
//           <input class="inp inp-mono" id="sc-clickCss"
//             value="${esc(s.clickCss)}"
//             placeholder=".submit-btn, #confirm-button"
//             oninput="updateField(${scIdx}, 'clickCss', this.value)">
//         </div>` : ''}
//
//         <!-- ── Test data (only URL, MODAL, and FORM_MODAL have test data) ── -->
//         ${cfg.hasData ? renderDataSection(scIdx, s, tab) : ''}
//
//         <!-- ── Assertions (only for ASSERT type) ── -->
//         ${s.type === 'ASSERT' ? assrtFunc.renderAssertionsSection(scIdx, s) : ''}
//
//       </div>
//     </div>`;
//     };
// }
