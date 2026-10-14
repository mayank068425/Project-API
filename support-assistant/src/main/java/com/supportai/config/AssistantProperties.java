package com.supportai.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Assistant behaviour, bound from configuration.
 */
@ConfigurationProperties(prefix = "supportai.assistant")
public class AssistantProperties {

    /**
     * Off by default, and that default is deliberate.
     *
     * <p>Incident management, the knowledge base, hybrid search, the dashboard and
     * audit all work without it. The assistant is an accelerator, not a
     * dependency. A support tool that stops working when a model provider has an
     * outage is a liability during exactly the incidents it exists for.
     */
    private boolean enabled = false;

    /** Temperature for anything parsed into a schema. Zero, so it is repeatable. */
    private double analysisTemperature = 0.0;

    /** Temperature for prose a human will edit before sending. */
    private double draftingTemperature = 0.4;

    private int retrievalTopK = 8;
    private double minSimilarity = 0.5;
    private Duration timeout = Duration.ofSeconds(60);
    private int maxIncidentCharacters = 40_000;

    /**
     * Non-assistant timeline entries required before an RCA is offered.
     *
     * <p>Below this the request is refused. A model asked to explain an incident
     * with two lines on its timeline produces a confident invented cause that is
     * indistinguishable from a real one.
     */
    private int minEvidenceEntries = 3;

    private int similarIncidentLimit = 5;

    /**
     * Half-life for precedent recency decay.
     *
     * <p>Infrastructure changes. A two-year-old fix often refers to a system that
     * no longer exists, and presenting it beside a recent one as equally relevant
     * is misleading.
     */
    private Duration precedentHalfLife = Duration.ofDays(180);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getAnalysisTemperature() {
        return analysisTemperature;
    }

    public void setAnalysisTemperature(double analysisTemperature) {
        this.analysisTemperature = analysisTemperature;
    }

    public double getDraftingTemperature() {
        return draftingTemperature;
    }

    public void setDraftingTemperature(double draftingTemperature) {
        this.draftingTemperature = draftingTemperature;
    }

    public int getRetrievalTopK() {
        return retrievalTopK;
    }

    public void setRetrievalTopK(int retrievalTopK) {
        this.retrievalTopK = retrievalTopK;
    }

    public double getMinSimilarity() {
        return minSimilarity;
    }

    public void setMinSimilarity(double minSimilarity) {
        this.minSimilarity = minSimilarity;
    }

    public Duration getTimeout() {
        return timeout;
    }

    public void setTimeout(Duration timeout) {
        this.timeout = timeout;
    }

    public int getMaxIncidentCharacters() {
        return maxIncidentCharacters;
    }

    public void setMaxIncidentCharacters(int maxIncidentCharacters) {
        this.maxIncidentCharacters = maxIncidentCharacters;
    }

    public int getMinEvidenceEntries() {
        return minEvidenceEntries;
    }

    public void setMinEvidenceEntries(int minEvidenceEntries) {
        this.minEvidenceEntries = minEvidenceEntries;
    }

    public int getSimilarIncidentLimit() {
        return similarIncidentLimit;
    }

    public void setSimilarIncidentLimit(int similarIncidentLimit) {
        this.similarIncidentLimit = similarIncidentLimit;
    }

    public Duration getPrecedentHalfLife() {
        return precedentHalfLife;
    }

    public void setPrecedentHalfLife(Duration precedentHalfLife) {
        this.precedentHalfLife = precedentHalfLife;
    }
}
