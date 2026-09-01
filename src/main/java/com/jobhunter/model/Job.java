package com.jobhunter.model;

import com.jobhunter.util.Text;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Job {

    private String id;
    private String source;
    private String title;
    private String company;
    private String location;
    private String description;
    private String url;
    private Double salaryMin;
    private Double salaryMax;
    private String currency;
    private Instant postedAt;
    private Instant fetchedAt;
    private List<String> additionalUrls;

    public Job() {
        this.fetchedAt = Instant.now();
    }

    public String deduplicationKey() {
        return Text.normalize(title) + "|" + Text.normalize(company);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSource() { return source; }
    public void setSource(String source) { this.source = source; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getCompany() { return company; }
    public void setCompany(String company) { this.company = company; }

    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public Double getSalaryMin() { return salaryMin; }
    public void setSalaryMin(Double salaryMin) { this.salaryMin = salaryMin; }

    public Double getSalaryMax() { return salaryMax; }
    public void setSalaryMax(Double salaryMax) { this.salaryMax = salaryMax; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public Instant getPostedAt() { return postedAt; }
    public void setPostedAt(Instant postedAt) { this.postedAt = postedAt; }

    public Instant getFetchedAt() { return fetchedAt; }
    public void setFetchedAt(Instant fetchedAt) { this.fetchedAt = fetchedAt; }

    public List<String> getAdditionalUrls() { return additionalUrls; }
    public void setAdditionalUrls(List<String> additionalUrls) { this.additionalUrls = additionalUrls; }
    public void addAdditionalUrl(String url) {
        if (this.additionalUrls == null) this.additionalUrls = new ArrayList<>();
        this.additionalUrls.add(url);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Job job = (Job) o;
        return Objects.equals(id, job.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return String.format("[%s] %s at %s (%s)", source, title, company, location);
    }
}
