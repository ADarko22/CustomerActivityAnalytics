package io.github.adarko22.customeractivityanalytics.risk.engine;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;

/**
 * Second line of defense behind the build-time {@link PromptContextMapper} allow-list: scans the
 * fully-assembled user prompt for PII-shaped content immediately before the model call. Patterns
 * are precompiled once at construction, not per-scan, so a startup-time cost pays for a per-request
 * check without harming performance (CLAUDE.md Global NFR).
 */
@Component
public class PiiGuardrailService {

  private final List<CompiledPattern> compiledPatterns;

  public PiiGuardrailService(PiiGuardrailProperties properties) {
    this.compiledPatterns =
        properties.patterns().stream()
            .map(p -> new CompiledPattern(p.name(), Pattern.compile(p.regex())))
            .toList();
  }

  /** Returns the violated pattern's name on a match, empty on a clean prompt. */
  public Optional<String> scan(String prompt) {
    return compiledPatterns.stream()
        .filter(cp -> cp.pattern().matcher(prompt).find())
        .map(CompiledPattern::name)
        .findFirst();
  }

  private record CompiledPattern(String name, Pattern pattern) {}
}
