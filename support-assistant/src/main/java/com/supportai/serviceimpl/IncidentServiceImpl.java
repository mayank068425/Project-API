package com.supportai.serviceimpl;

import com.supportai.dto.request.IncidentRequest;
import com.supportai.dto.request.TimelineEntryRequest;
import com.supportai.dto.response.IncidentResponse;
import com.supportai.dto.response.TimelineEntryResponse;
import com.supportai.entity.Incident;
import com.supportai.entity.IncidentStatus;
import com.supportai.entity.TimelineEntry;
import com.supportai.exception.ResourceNotFoundException;
import com.supportai.mapper.IncidentMapper;
import com.supportai.repository.IncidentRepository;
import com.supportai.repository.TimelineEntryRepository;
import com.supportai.security.SecurityUtils;
import com.supportai.service.AuditService;
import com.supportai.service.IncidentService;
import com.supportai.util.Constants;
import com.supportai.validation.IncidentValidator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class IncidentServiceImpl implements IncidentService {

    private static final Logger log = LoggerFactory.getLogger(IncidentServiceImpl.class);

    private final IncidentRepository incidents;
    private final TimelineEntryRepository timeline;
    private final IncidentMapper mapper;
    private final IncidentValidator validator;
    private final AuditService audit;
    private final JdbcTemplate jdbc;

    public IncidentServiceImpl(IncidentRepository incidents, TimelineEntryRepository timeline,
                               IncidentMapper mapper, IncidentValidator validator,
                               AuditService audit, JdbcTemplate jdbc) {
        this.incidents = incidents;
        this.timeline = timeline;
        this.mapper = mapper;
        this.validator = validator;
        this.audit = audit;
        this.jdbc = jdbc;
    }

    @Override
    @Transactional
    public IncidentResponse create(IncidentRequest request) {
        validator.validateForCreate(request);

        Incident incident = Incident.builder()
                .incidentNumber(nextIncidentNumber())
                .title(request.title().strip())
                .description(request.description())
                .severity(request.severity())
                .status(IncidentStatus.NEW)
                .service(request.service().strip())
                .customerImpacting(Boolean.TRUE.equals(request.customerImpacting()))
                .detectedAt(request.detectedAt())
                .reportedBy(SecurityUtils.currentUsername())
                .assignedTo(request.assignedTo())
                .build();
        incidents.save(incident);

        audit.record(AuditService.INCIDENT_CREATED, "Incident", incident.getIncidentNumber(),
                incident.getSeverity() + " on " + incident.getService());
        log.info("opened {} ({} on {})", incident.getIncidentNumber(),
                incident.getSeverity(), incident.getService());
        return mapper.toResponse(incident, 0);
    }

    /**
     * Issues the next incident number.
     *
     * <p>{@code LAST_INSERT_ID}-style atomic increment rather than
     * {@code MAX(number)+1}: two incidents opened in the same second — which is
     * exactly what happens when one outage triggers several alerts — would
     * otherwise read the same maximum and one would fail on the unique index.
     *
     * <p>PostgreSQL's {@code RETURNING} does this in one statement. Its own
     * transaction, so a number consumed by a create that later rolls back is
     * skipped rather than reused.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public String nextIncidentNumber() {
        Long value = jdbc.queryForObject(
                "UPDATE number_sequence SET next_value = next_value + 1 "
                        + "WHERE sequence_name = ? RETURNING next_value - 1",
                Long.class, Constants.INCIDENT_SEQUENCE);
        if (value == null) {
            throw new IllegalStateException("The INCIDENT sequence is missing. "
                    + "Check that data.sql ran.");
        }
        return String.format(Constants.INCIDENT_NUMBER_FORMAT, value);
    }

    @Override
    @Transactional
    public IncidentResponse update(Long id, IncidentRequest request) {
        Incident incident = requireIncident(id);
        validator.validateForUpdate(request, incident);

        if (request.title() != null) {
            incident.setTitle(request.title().strip());
        }
        if (request.description() != null) {
            incident.setDescription(request.description());
        }
        if (request.severity() != null) {
            incident.setSeverity(request.severity());
        }
        if (request.service() != null) {
            incident.setService(request.service().strip());
        }
        if (request.customerImpacting() != null) {
            incident.setCustomerImpacting(request.customerImpacting());
        }
        if (request.detectedAt() != null) {
            incident.setDetectedAt(request.detectedAt());
        }
        if (request.assignedTo() != null) {
            incident.setAssignedTo(request.assignedTo());
        }

        incidents.save(incident);
        audit.record(AuditService.INCIDENT_UPDATED, "Incident", incident.getIncidentNumber(),
                null);
        return mapper.toResponse(incident, timeline.countByIncidentId(id));
    }

    /**
     * Moves an incident through its lifecycle and records it on the timeline.
     *
     * <p>The status change is written as a timeline entry as well as a field
     * update, because a reader reconstructing the incident needs the sequence, not
     * just the endpoint.
     */
    @Override
    @Transactional
    public IncidentResponse transition(Long id, IncidentStatus next, Instant at) {
        Incident incident = requireIncident(id);
        IncidentStatus before = incident.getStatus();

        incident.transitionTo(next, at);
        incidents.save(incident);

        timeline.save(TimelineEntry.builder()
                .incidentId(id)
                .occurredAt(at == null ? Instant.now() : at)
                .entryType(TimelineEntry.EntryType.STATUS_CHANGE)
                .source(TimelineEntry.EntrySource.HUMAN)
                .content(before + " -> " + next)
                .author(SecurityUtils.currentUsername())
                .build());

        audit.record(AuditService.INCIDENT_TRANSITIONED, "Incident",
                incident.getIncidentNumber(), before + " -> " + next);
        log.info("{} {} -> {}", incident.getIncidentNumber(), before, next);
        return mapper.toResponse(incident, timeline.countByIncidentId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse get(Long id) {
        return mapper.toResponse(requireIncident(id), timeline.countByIncidentId(id));
    }

    @Override
    @Transactional(readOnly = true)
    public IncidentResponse getByNumber(String incidentNumber) {
        Incident incident = incidents.findByIncidentNumber(incidentNumber)
                .orElseThrow(() -> new ResourceNotFoundException("No incident " + incidentNumber));
        return mapper.toResponse(incident, timeline.countByIncidentId(incident.getId()));
    }

    @Override
    @Transactional(readOnly = true)
    public Page<IncidentResponse> list(IncidentStatus status, String service, Pageable pageable) {
        Page<Incident> page;
        if (status != null) {
            page = incidents.findByStatus(status, pageable);
        } else if (service != null && !service.isBlank()) {
            page = incidents.findByService(service.strip(), pageable);
        } else {
            page = incidents.findAll(pageable);
        }
        // The count is not fetched per row here: a list of fifty incidents would
        // be fifty count queries. Zero is honest for a list view; the detail
        // endpoint carries the real figure.
        return page.map(mapper::toResponse);
    }

    @Override
    @Transactional(readOnly = true)
    public List<IncidentResponse> open() {
        return incidents.findOpen().stream().map(mapper::toResponse).toList();
    }

    @Override
    @Transactional
    public TimelineEntryResponse addTimelineEntry(Long incidentId, TimelineEntryRequest request) {
        requireIncident(incidentId);

        TimelineEntry entry = TimelineEntry.builder()
                .incidentId(incidentId)
                // Defaults to now, but the caller can say when it actually
                // happened. Engineers write up the last twenty minutes once the
                // fire is out, and insertion order is not event order.
                .occurredAt(request.occurredAt() == null ? Instant.now() : request.occurredAt())
                .entryType(request.entryType())
                .source(TimelineEntry.EntrySource.HUMAN)
                .content(request.content())
                .author(SecurityUtils.currentUsername())
                .build();
        timeline.save(entry);

        audit.record(AuditService.TIMELINE_ENTRY_ADDED, "Incident", String.valueOf(incidentId),
                request.entryType().name());
        return mapper.toResponse(entry);
    }

    /**
     * Records assistant output on the timeline.
     *
     * <p>Source ASSISTANT, which excludes it from evidence. This is what stops a
     * later RCA grounding itself on an earlier RCA's guesses — after a couple of
     * rounds of that, the conclusion traces back to nothing that happened while
     * still citing references that resolve.
     */
    @Override
    @Transactional
    public TimelineEntryResponse recordAssistantEntry(Long incidentId, String content) {
        requireIncident(incidentId);

        TimelineEntry entry = TimelineEntry.builder()
                .incidentId(incidentId)
                .occurredAt(Instant.now())
                .entryType(TimelineEntry.EntryType.ANALYSIS)
                .source(TimelineEntry.EntrySource.ASSISTANT)
                .content(content)
                .author("assistant")
                .build();
        timeline.save(entry);
        return mapper.toResponse(entry);
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEntryResponse> timeline(Long incidentId) {
        requireIncident(incidentId);
        return timeline.findByIncidentIdOrderByOccurredAtAsc(incidentId).stream()
                .map(mapper::toResponse).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<TimelineEntry> evidenceFor(Long incidentId) {
        return timeline.findEvidenceFor(incidentId);
    }

    @Override
    @Transactional(readOnly = true)
    public Incident requireIncident(Long id) {
        return incidents.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("incident", id));
    }
}
