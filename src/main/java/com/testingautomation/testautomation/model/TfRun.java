package com.testingautomation.testautomation.model;


import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.index.Indexed;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Document(collection = "tf_runs")
public class TfRun {

    @Id
    private String id;

    @Indexed
    private String projectId;

    private String runName;

    /**
     * Status: "success" | "error" | "running"
     */
    private String status;

    private int httpStatus;

    /**
     * The testResultStatement sent to /run-auth
     */
    private String successMsg;

    private int    scenarioCount;
    private long   elapsed;          // ms

    /**
     * Raw JSON response from /run-auth stored as a free-form Map
     */
    private Map<String, Object> responseData;

    /**
     * Step snapshots recorded at run time
     */
    private List<StepSnapshot> steps;

    /**
     * Optional screenshot references — e.g. {name, url} once backend supports it
     */
    private List<ScreenshotRef> screenshots;

    private LocalDateTime ts = LocalDateTime.now();

    // ── nested types ─────────────────────────────────────────────
    public static class StepSnapshot {
        private String sid;
        private String type;   // NAVIGATE | FILL_FORM | CLICK | OPEN_MODAL | ASSERT
        private String label;
        private String summary;
        private boolean hasData;

        public String getSid()            { return sid; }
        public void   setSid(String sid)  { this.sid = sid; }
        public String getType()               { return type; }
        public void   setType(String type)    { this.type = type; }
        public String getLabel()              { return label; }
        public void   setLabel(String label)  { this.label = label; }
        public String getSummary()                { return summary; }
        public void   setSummary(String summary)  { this.summary = summary; }
        public boolean isHasData()             { return hasData; }
        public void    setHasData(boolean v)   { this.hasData = v; }
    }

    public static class ScreenshotRef {
        private String name;
        private String url;      // or dataUrl for base64
        private String stepId;

        public String getName()           { return name; }
        public void   setName(String n)   { this.name = n; }
        public String getUrl()            { return url; }
        public void   setUrl(String u)    { this.url = u; }
        public String getStepId()             { return stepId; }
        public void   setStepId(String s)     { this.stepId = s; }
    }

    // ── constructors ─────────────────────────────────────────────
    public TfRun() {}

    // ── getters / setters ─────────────────────────────────────────
    public String getId()           { return id; }
    public void   setId(String id)  { this.id = id; }

    public String getProjectId()                  { return projectId; }
    public void   setProjectId(String projectId)  { this.projectId = projectId; }

    public String getRunName()              { return runName; }
    public void   setRunName(String v)      { this.runName = v; }

    public String getStatus()               { return status; }
    public void   setStatus(String status)  { this.status = status; }

    public int  getHttpStatus()             { return httpStatus; }
    public void setHttpStatus(int v)        { this.httpStatus = v; }

    public String getSuccessMsg()               { return successMsg; }
    public void   setSuccessMsg(String v)       { this.successMsg = v; }

    public int  getScenarioCount()          { return scenarioCount; }
    public void setScenarioCount(int v)     { this.scenarioCount = v; }

    public long getElapsed()                { return elapsed; }
    public void setElapsed(long elapsed)    { this.elapsed = elapsed; }

    public Map<String, Object> getResponseData()             { return responseData; }
    public void                setResponseData(Map<String, Object> v) { this.responseData = v; }

    public List<StepSnapshot> getSteps()              { return steps; }
    public void               setSteps(List<StepSnapshot> s) { this.steps = s; }

    public List<ScreenshotRef> getScreenshots()                  { return screenshots; }
    public void                setScreenshots(List<ScreenshotRef> s) { this.screenshots = s; }

    public LocalDateTime getTs()              { return ts; }
    public void          setTs(LocalDateTime v){ this.ts = v; }
}