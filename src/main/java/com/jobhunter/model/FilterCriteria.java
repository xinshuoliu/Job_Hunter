package com.jobhunter.model;

import java.util.List;

public class FilterCriteria {

    public enum ExcludeScope { TITLE, ALL }

    private List<String> requiredKeywords;
    private List<String> keywords;
    private List<String> excludeKeywords;
    private List<String> locations;
    private Double minSalary;
    private String currency;
    private ExcludeScope excludeScope = ExcludeScope.TITLE;

    public FilterCriteria() {}

    public List<String> getRequiredKeywords() { return requiredKeywords; }
    public void setRequiredKeywords(List<String> requiredKeywords) { this.requiredKeywords = requiredKeywords; }

    public List<String> getKeywords() { return keywords; }
    public void setKeywords(List<String> keywords) { this.keywords = keywords; }

    public List<String> getExcludeKeywords() { return excludeKeywords; }
    public void setExcludeKeywords(List<String> excludeKeywords) { this.excludeKeywords = excludeKeywords; }

    public List<String> getLocations() { return locations; }
    public void setLocations(List<String> locations) { this.locations = locations; }

    public Double getMinSalary() { return minSalary; }
    public void setMinSalary(Double minSalary) { this.minSalary = minSalary; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public ExcludeScope getExcludeScope() { return excludeScope; }
    public void setExcludeScope(ExcludeScope excludeScope) { this.excludeScope = excludeScope; }
}
