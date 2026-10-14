package com.supportai.service;

import com.supportai.dto.request.IncidentRequest;
import com.supportai.dto.request.TimelineEntryRequest;
import com.supportai.dto.response.IncidentResponse;
import com.supportai.dto.response.TimelineEntryResponse;
import com.supportai.entity.Incident;
import com.supportai.entity.IncidentStatus;
import com.supportai.entity.TimelineEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;

public interface IncidentService {

    IncidentResponse create(IncidentRequest request);

    IncidentResponse update(Long id, IncidentRequest request);

    /**
     * Moves an incident through its lifecycle.
     *
     * @param at when it happened, or null for now. A lead who acknowledged on the
     *           phone twenty minutes before opening the tool needs to say so, or
     *           every response metric is wrong by their typing speed.
     */
    IncidentResponse transition(Long id, IncidentStatus next, Instant at);

    IncidentResponse get(Long id);

    IncidentResponse getByNumber(String incidentNumber);

    Page<IncidentResponse> list(IncidentStatus status, String service, Pageable pageable);

    List<IncidentResponse> open();

    TimelineEntryResponse addTimelineEntry(Long incidentId, TimelineEntryRequest request);

    /**
     * Records assistant output on the timeline for traceability.
     *
     * <p>Written with source ASSISTANT, which excludes it from being cited as
     * evidence by a later analysis. Without that exclusion an RCA would end up
     * grounded on its own earlier guesses.
     */
    TimelineEntryResponse recordAssistantEntry(Long incidentId, String content);

    List<TimelineEntryResponse> timeline(Long incidentId);

    /** Only entries a generated analysis is allowed to cite. */
    List<TimelineEntry> evidenceFor(Long incidentId);

    Incident requireIncident(Long id);
}
