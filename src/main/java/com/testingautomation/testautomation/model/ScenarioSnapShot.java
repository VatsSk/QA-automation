package com.testingautomation.testautomation.model;

public class ScenarioSnapShot {
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
