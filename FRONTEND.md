# QA Studio — Frontend Documentation

> **Stack:** Vanilla HTML · Vanilla CSS (`main.css`) · ES Modules (no bundler)
> **Backend:** Spring Boot REST API at `http://localhost:8088`
> **Auth:** JWT stored in `localStorage` (`qa_token`, `qa_user`)
> **Theme:** Dark/Light toggle persisted via `localStorage`

---

## File Structure

```
src/main/resources/static/
|
+-- index.html            # Legacy test-block builder (standalone, not part of main app flow)
+-- login.html            # Entry point — authentication
+-- projects.html         # Projects list (home after login)
+-- modules.html          # Modules inside a project
+-- flows.html            # Flows inside a module
+-- flow-detail.html      # Flow execution detail view
+-- runs.html             # Runs inside a module
+-- run-editor.html       # Run editor (Monaco-based, scenario builder)
+-- run-detail.html       # Run execution result detail
+-- component.html        # Component / step editor
|
+-- css/
|   +-- main.css          # Primary design system (tokens, layout, components)
|   +-- styles.css        # Legacy styles (used only by index.html)
|
+-- js/
    +-- api.js            # All HTTP calls to the backend (single source of truth)
    +-- utils.js          # Shared helpers: toast, modal, auth guard, sidebar, theme
    +-- config.js         # Step-type definitions (TYPES constant)
    +-- runs.js           # Run-related logic (used by run-editor)
    +-- script.js         # Legacy script (used only by index.html)
    +-- payload.js        # Payload builders for run scenarios
    +-- dlUtils.js        # DL (Data-Layer) mode utilities
    +-- jsonToDLParser.js # Converts JSON scenarios to DL format
    +-- assertions_functions.js  # Assertion helper functions
    +-- form-modal-payload.js    # Modal payload form utilities
    +-- form-modal-support.js    # Modal form support logic
```

---

## Page Navigation Flow

```
login.html
    |
    +---> projects.html  (auto-redirect if already logged in)
              |
              +---> modules.html?projectId={id}
              |         |
              |         +---> flows.html?projectId={id}&moduleId={id}
              |         |         |
              |         |         +---> flow-detail.html?flowId={id}&projectId={id}&moduleId={id}
              |         |
              |         +---> runs.html?projectId={id}&moduleId={id}
              |                   |
              |                   +---> run-editor.html?projectId={id}&moduleId={id}[&runId={id}]
              |                   +---> run-detail.html?runId={id}&projectId={id}&moduleId={id}
              |
              +---> component.html?projectId={id}[&moduleId={id}][&componentId={id}]
```

---

## Page-by-Page Breakdown

### 1. `login.html` — Sign In

**Purpose:** Authenticates the user and stores the JWT token.

**Layout:**
```
+-----------------------------+
|  [QA]  QA Studio            |
|        Test Automation Mgr  |  [theme toggle]
|                             |
|  Username ________________  |
|  Password ________________  |
|                             |
|  [ Sign In ->        ]      |
|                             |
|  Accounts via POST /api/users|
+-----------------------------+
```

**Flow:**
1. On load — if `qa_token` + `qa_user` exist in `localStorage` -> redirect to `projects.html`
2. User enters credentials -> `doLogin()` -> `POST /api/auth/login`
3. On success -> stores token & user in `localStorage` -> redirects to `projects.html`
4. On failure -> shows error toast

---

### 2. `projects.html` — All Projects

**Purpose:** Lists all projects belonging to the logged-in user. Serves as the main dashboard.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: [All Projects]              [theme]      |
|          +----------------------------------------------------+
| Projects |  Page Header: "All Projects"    [+ New Project]   |
| Modules  |                                                   |
| Flows    |  +----------------------------------------------+ |
| Runs     |  |  Chrome Extension ID: [_________] [Save]     | |
|          |  +----------------------------------------------+ |
|          |                                                   |
|          |  +-----------+  +-----------+  +-----------+     |
|          |  |  Project  |  |  Project  |  |  Project  |     |
|          |  |  Name     |  |  Name     |  |  Name     |     |
|          |  |  Desc     |  |  Desc     |  |  Desc     |     |
|          |  | [Run All] |  | [Run All] |  | [Run All] |     |
|          |  | [Edit][X] |  | [Edit][X] |  | [Edit][X] |     |
|          |  +-----------+  +-----------+  +-----------+     |
+----------+----------------------------------------------------+
```

**Key Features:**
- **Project Card** click -> opens `modules.html?projectId={id}`
- **Run All Flows** -> `POST /api/flows/execute-project/{id}`, toggles to Stop button while running
- **Edit** -> modal: Name, Description, Login URL, CSV upload
- **Delete** -> confirmation modal -> `DELETE /api/projects/{id}`
- **+ New Project** -> modal: Name*, Description, Login URL*, CSV credentials file*
- **Chrome Extension ID** -> saved per-user for Smart Recorder integration

---

### 3. `modules.html` — Modules

**Purpose:** Lists all modules in a project. Also contains a floating **Run Queue** widget.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > {Project}            [theme]  |
|          +----------------------------------------------------+
| Projects |  "Modules"                    [+ New Module]      |
| Modules  |                                                   |
| Flows    |  [Search by name...]                   [tabs]     |
| Runs     |                                                   |
|          |  +----------------------------------------------+ |
|          |  | Name  | Runs | Last Run | Status | Actions  | |
|          |  +-------+------+----------+--------+----------+ |
|          |  | Mod A | 12   | 2h ago   | PASS   | [View]   | |
|          |  | Mod B |  3   | 5d ago   | FAIL   | [View]   | |
|          |  +----------------------------------------------+ |
|          |                                                   |
|          |              +------------------------------+     |
|          |              | Run Queue          [^][v]   |     |
|          |              |  [Search flows...]           |     |
|          |              |  Flow A              [x]     |     |
|          |              |  Flow B              [x]     |     |
|          |              |  [Execute Queue]             |     |
|          |              +------------------------------+     |
+----------+----------------------------------------------------+
```

**Key Features:**
- Click module row -> navigates to `flows.html?projectId=...&moduleId=...`
- **Run Queue Widget** (bottom-right, floating, collapsible):
  - Search and add any flow across modules into a queue
  - Execute all queued flows in sequence -> `POST /api/flows/execute-queue`
- **Environments** tab -> manage base URLs per environment per project

---

### 4. `flows.html` — Flows

**Purpose:** Lists all automation flows within a module. Supports search, execute, clone, delete.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > {Proj} > {Module}   [theme]  |
|          +----------------------------------------------------+
| Projects |  "{Module} Flows"                [+ New Flow]     |
| Modules  |                                                   |
| Flows <- |  [Search by name...]                              |
| Runs     |                                                   |
|          |  +----------------------------------------------+ |
|          |  | Flow Name | Status | Steps | Duration | Upd. | |
|          |  +-----------+--------+-------+----------+------+ |
|          |  | Login Flow| PASS   |  12   |  4.2s    | 2h   | |
|          |  |   [View][Edit][Clone][Run][Delete]           | |
|          |  | Search Flw| FAIL   |   8   |  1.1s    | 5d   | |
|          |  |   [View][Edit][Clone][Run][Stop][Delete]     | |
|          |  +----------------------------------------------+ |
|          |  Pagination: [< 1 / 3 >]                         |
+----------+----------------------------------------------------+
```

**Key Features:**
- **+ New Flow** -> launches Chrome extension via `chrome.runtime.sendMessage` with `START_RECORDING` (Smart Recorder)
- **Edit** -> re-launches Smart Recorder with existing flow data pre-loaded
- **View** -> navigates to `flow-detail.html`
- **Clone** -> `POST /api/flows/{id}/clone`
- **Run** -> environment picker modal -> `POST /api/flows/{id}/run?environmentId={envId}`
- **Stop** -> only shown when `executionStatus === 'RUNNING'` -> `POST /api/flows/{id}/stop`
- **Delete** -> confirmation modal -> `DELETE /api/flows/{id}`
- Client-side search + client-side pagination (20 per page)

---

### 5. `flow-detail.html` — Flow Execution Detail

**Purpose:** Shows step-by-step execution results, screenshots, and stats for a specific flow.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > {Proj} > {Module}   [theme]  |
|          +----------------------------------------------------+
|          |  +----------------------------------------------+ |
|          |  | Flow Hero Card                               | |
|          |  |  Flow Name  [PASS]    [Run]  [Stop]         | |
|          |  |  Description text...                        | |
|          |  |  +------+ +------+ +------+ +------+        | |
|          |  |  |Steps | | Pass | | Fail | | Dur  |        | |
|          |  |  |  12  | |  10  | |   2  | | 4.2s |        | |
|          |  |  +------+ +------+ +------+ +------+        | |
|          |  +----------------------------------------------+ |
|          |                                                   |
|          |  [All] [Pass] [Fail] [Skip]   [Search steps]    |
|          |                                                   |
|          |  Step 1 -- CLICK ------------------ PASS         |
|          |    Selector: #login-btn                          |
|          |    [screenshot thumbnail]                        |
|          |  Step 2 -- INPUT ------------------ PASS         |
|          |  Step 3 -- ASSERT ----------------- FAIL         |
|          |    Expected: "Welcome" | Actual: "Error"         |
+----------+----------------------------------------------------+
```

**Key Features:**
- Flow hero with aggregated stats (total steps, passed, failed, duration, start/end times)
- Step filter bar (All / Pass / Fail / Skip) + text search
- Each step card: type, selector/value, status badge, error message, screenshot thumbnail
- Screenshots fetched via pre-signed S3 URL (`GET /api/files/presign?key=...`)
- Re-execute and Stop flow buttons

---

### 6. `runs.html` — Runs

**Purpose:** Lists all test runs in a module with status filters and search.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > {Proj} > {Module}   [theme]  |
|          +----------------------------------------------------+
|          |  "Runs"                         [+ New Run]       |
|          |                                                   |
|          |  [All][PASS][FAIL][RUNNING][PENDING] [Search]    |
|          |                                                   |
|          |  +----------------------------------------------+ |
|          |  | Name | Status | Scenarios | Duration | Date  | |
|          |  +------+--------+-----------+----------+-------+ |
|          |  | Smoke Test | PASS | 5 | 12.3s | 2h ago       | |
|          |  |   [View][Edit][Clone][Run][Delete]           | |
|          |  +----------------------------------------------+ |
|          |  Pagination: [< 1 / 5 >]                         |
+----------+----------------------------------------------------+
```

**Key Features:**
- Status filter tabs (All, PASS, FAIL, RUNNING, PENDING...)
- **+ New Run** -> opens `run-editor.html`
- **Edit** -> choice of Manual Mode or DL Mode -> `run-editor.html?runId={id}`
- **View** -> `run-detail.html?runId={id}`
- **Clone** -> `POST /api/runs/{id}/clone`
- **Run** -> `POST /api/runs/{id}/execute`
- **Delete** -> confirmation -> `DELETE /api/runs/{id}`
- Server-side pagination, client-side status filter chips

---

### 7. `run-editor.html` — Run Editor

**Purpose:** Full-screen editor for building/editing test runs. Uses Monaco Editor for DL mode.

**Layout:**
```
+-------------------------------------------------------------------+
| TOPBAR: [<- Back]  Projects > {Proj} > {Mod} > {Run}            |
|                        [Save Draft]  [Save & Run]  [theme]       |
+---------------+---------------------------------------------------+
| SCENARIO      |  Run Details Card                                 |
| SIDEBAR       |  +----------------------------------------------+ |
|               |  | Run Name: [_______]  Tags: [_______]        | |
| Scenarios [3] |  | Description: [__________________________]   | |
| +-----------+ |  +----------------------------------------------+ |
| | Scenario 1| |                                                  |
| | Scenario 2| |  Scenario Form Card                              |
| | Scenario 3| |  +----------------------------------------------+ |
| +-----------+ |  | Scenario Name   Type: [API v]  [Add Step]   | |
| [+ Add Scen.] |  |                                              | |
|               |  | Step 1: [CLICK v]  Selector [_________]    | |
|               |  | Step 2: [INPUT v]  Value    [_________]    | |
|               |  |        (drag to reorder)                    | |
|               |  +----------------------------------------------+ |
+---------------+---------------------------------------------------+
```

**Key Features:**
- Full-screen layout (no sidebar — maximized editor focus)
- Left panel: scenario list with drag-and-drop reorder
- Right panel: active scenario editor with step type selector
- Step types defined in `config.js` -> `TYPES` (CLICK, INPUT, NAVIGATE, ASSERT, etc.)
- **Manual Mode** — form-based step builder (uses `form-modal-support.js`)
- **DL Mode** — Monaco Editor with JSON schema, parsed via `jsonToDLParser.js`
- CSV upload per scenario (test data from S3)
- Save as draft or Save & Run immediately

---

### 8. `run-detail.html` — Run Detail

**Purpose:** Shows detailed execution results including per-scenario CSV result tables and screenshots.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > {Proj} > {Mod} > Run Detail  |
|          +----------------------------------------------------+
|          |  Run Name  [STATUS]  [Edit]  [Re-run]             |
|          |  Created: ...  Duration: ...  Scenarios: N        |
|          |                                                   |
|          |  +----------------------------------------------+ |
|          |  | Scenario 1 --------------------  PASS  [v]  | |
|          |  |  +--CSV Result Table-----------+            | |
|          |  |  | testcaseId | status | error | [img]     | |
|          |  |  | TC_001     | PASS   |        |           | |
|          |  |  | TC_002     | FAIL   | msg    |           | |
|          |  |  +-------------------------------------------+ |
|          |  | Scenario 2 --------------------  FAIL  [v]  | |
|          |  +----------------------------------------------+ |
+----------+----------------------------------------------------+
```

**Key Features:**
- Run summary header (status, timing, counts)
- Each scenario is collapsible; shows CSV result table fetched from S3
- Screenshot viewer: click row -> loads screenshots via `GET /scenario-screenshots?prefix=...`
- **Edit** -> modal: choose Manual Mode or DL Mode -> navigates to `run-editor.html`
- **Re-run** -> `POST /api/runs/{id}/execute`

---

### 9. `component.html` — Component Editor

**Purpose:** Manage reusable component step definitions across modules.

**Layout:**
```
+----------+----------------------------------------------------+
| SIDEBAR  |  TOPBAR: Projects > Component Editor      [theme] |
|          +----------------------------------------------------+
|          |  "Component Editor"   [+ New Component]           |
|          |                                                   |
|          |  Module: [Select Module v]                        |
|          |  Component: [Select Component v]                  |
|          |                                                   |
|          |  Steps:                                           |
|          |  +--------------------------------------------+  |
|          |  | 1. CLICK   #submit-btn       [Edit] [Del] |  |
|          |  | 2. INPUT   #username "admin" [Edit] [Del] |  |
|          |  +--------------------------------------------+  |
|          |  [+ Add Step]                                     |
|          |                                                   |
|          |  [Save Component]                                 |
+----------+----------------------------------------------------+
```

---

## Shared Architecture

### Layout System (`app-layout`)

Every authenticated page uses the same shell structure:

```
+----------------------------------------------+
|  app-layout  (CSS grid: sidebar + main-col)  |
|  +----------+--------------------------------+|
|  | sidebar  | main-col                      ||
|  | (built   |  +-----------------------+    ||
|  |  by JS)  |  | topbar (breadcrumbs)  |    ||
|  |          |  +-----------------------+    ||
|  |          |  +-----------------------+    ||
|  |          |  | main-scroll > .page   |    ||
|  |          |  |   page-header         |    ||
|  |          |  |   content area        |    ||
|  |          |  +-----------------------+    ||
|  +----------+--------------------------------+|
+----------------------------------------------+
```

### Sidebar (built dynamically by `utils.js -> buildSidebar()`)

```
+----------------+
|  QA  QA Studio |
|                |
|  Navigation:   |
|  > Projects    |
|  > Modules     |
|  > Flows       |
|  > Runs        |
|                |
|  ------------- |
|  Username      |
|  [Logout]      |
+----------------+
```

---

## JavaScript Modules

| File | Responsibility |
|------|----------------|
| `api.js` | All HTTP requests. Exports: `auth`, `users`, `projects`, `modules`, `runs`, `flows`, `environments`, `files`, `uploads`, `components` |
| `utils.js` | `toast()`, `openModal()`, `closeModal()`, `requireLogin()`, `logout()`, `initTheme()`, `toggleTheme()`, `buildSidebar()`, `statusBadge()`, `timeAgo()`, `paginationHTML()`, `debounce()` |
| `config.js` | `TYPES` — step type definitions (label, fields, defaults) |
| `runs.js` | Run-specific UI logic for `run-editor.html` |
| `payload.js` | Builds API payload objects from form state |
| `dlUtils.js` | DL mode utilities (serialize/deserialize DL steps) |
| `jsonToDLParser.js` | Converts JSON step arrays to DL format strings |
| `assertions_functions.js` | Assertion type helpers |
| `form-modal-payload.js` | Step form to payload conversion |
| `form-modal-support.js` | Step add/edit modal rendering |

---

## Authentication Guard

Every authenticated page calls `requireLogin()` from `utils.js` on load:

```js
const user = requireLogin();
// Checks: localStorage.getItem('qa_token') && localStorage.getItem('qa_user')
// If not present -> redirects to /login.html
```

---

## API Endpoints Used

| Resource | Endpoints |
|----------|-----------|
| Auth | `POST /api/auth/login` |
| Users | `GET /api/users/{username}/extension`, `PUT /api/users/{username}/extension` |
| Projects | `GET /api/projects`, `POST /api/projects`, `GET/PUT/DELETE /api/projects/{id}` |
| Modules | `GET /api/projects/{pid}/modules`, `GET/PUT/DELETE /api/modules/{id}` |
| Runs | `GET/POST /api/projects/{pid}/modules/{mid}/runs`, `GET/PUT/DELETE /api/runs/{id}`, `POST /api/runs/{id}/execute`, `POST /api/runs/{id}/clone` |
| Flows | `GET /api/flows/{pid}/{mid}`, `GET/PUT/DELETE /api/flows/{id}`, `POST /api/flows/{id}/run`, `POST /api/flows/{id}/stop`, `POST /api/flows/{id}/clone`, `POST /api/flows/execute-queue`, `POST /api/flows/execute-module/{mid}`, `POST /api/flows/execute-project/{pid}`, `POST /api/flows/stop-project/{pid}` |
| Environments | `GET/POST /api/projects/{pid}/environments`, `PUT/DELETE /api/projects/{pid}/environments/{id}` |
| Uploads | `POST /api/uploads/testcase`, `POST /api/uploads/project-login` |
| Files | `GET /api/files/presign?key=...` |
| Screenshots | `GET /scenario-screenshots?prefix=...` |
| Components | `GET/POST /api/components`, `PUT /api/components/{id}`, `GET /api/components/modules/{pid}`, `GET /api/components/{pid}/{mid}`, `GET/PUT /api/components/flow-info/{flowId}` |

---

## Design System (`main.css`)

| CSS Token | Usage |
|-----------|-------|
| `--bg`, `--bg2` | Page and surface backgrounds |
| `--sur`, `--sur2`, `--sur3` | Card and elevated surfaces |
| `--bd`, `--bd2` | Border colors |
| `--tx`, `--tx2`, `--tx3` | Text: primary / secondary / muted |
| `--pri` | Primary accent color (blue) |
| `--r`, `--rl` | Border radius: small / large |
| `--sh` | Box shadow |
| `--inp` | Input field background |

**Fonts:** `DM Sans` (UI text) · `JetBrains Mono` (code and mono fields)

**Status Badge Classes:** `.badge-pass` (green) · `.badge-fail` (red) · `.badge-running` (amber) · `.badge-pending` (muted) · `.badge-blue` (info)

---

## Chrome Extension / Smart Recorder Integration

Flows are created and edited via a Chrome Extension. The integration flow:

1. User clicks **+ New Flow** or **Edit** on `flows.html`
2. App fetches `extensionId` from `GET /api/users/{username}/extension`
3. App calls `chrome.runtime.sendMessage(extensionId, { action: "START_RECORDING", projectId, moduleId, url, flag: "FLOW", ... })`
4. Extension opens a new Chrome window at the project's `loginUrl`
5. User records steps in the browser; extension persists the flow back to the API
6. `flows.html` refreshes its cache and re-renders the flow list

For **editing** an existing flow, `existingFlow` and `flowId` are also passed in the message so the extension can pre-populate the recorded steps.
