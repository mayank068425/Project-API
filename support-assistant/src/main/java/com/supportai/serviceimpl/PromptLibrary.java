package com.supportai.serviceimpl;

import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * System prompts, loaded from resources at start-up.
 *
 * <p>Prompts are configuration, not code. They get revised far more often than
 * the classes around them — usually by someone reacting to a bad output — and
 * keeping them in files makes that revision a reviewable diff rather than a
 * string edit buried in a service method.
 *
 * <p>Loaded eagerly and validated, so a missing or empty prompt fails the
 * deployment rather than the first incident that needs it.
 */
@Component
public class PromptLibrary {

    private static final List<String> REQUIRED = List.of(
            "rca-analysis", "knowledge-qa", "incident-summary",
            "customer-update", "post-incident-report");

    private final Map<String, String> prompts = new HashMap<>();

    public PromptLibrary() {
        REQUIRED.forEach(name -> prompts.put(name, load(name)));
    }

    public String get(String name) {
        String prompt = prompts.get(name);
        if (prompt == null) {
            throw new IllegalArgumentException("No prompt named " + name);
        }
        return prompt;
    }

    private String load(String name) {
        String location = "prompts/" + name + ".txt";
        try (var in = new ClassPathResource(location).getInputStream()) {
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8).trim();
            if (content.isEmpty()) {
                throw new IllegalStateException(location + " is empty");
            }
            return content;
        } catch (IOException ex) {
            throw new UncheckedIOException("Could not read " + location, ex);
        }
    }
}
