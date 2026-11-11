package com.dockmind.ai;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

/** Returns the kit's canned answers. The default extractor, so the loop runs with no API key. */
@Component
@ConditionalOnProperty(name = "extractor.type", havingValue = "fixture", matchIfMissing = true)
public class FixtureExtractor implements Extractor {
    static final String GARBAGE = "garbage";
    static final String GARBAGE_TEXT = "Sorry, I could not make out this delivery note.";

    private static final Set<String> KNOWN = Set.of("note-1", "note-2", "note-3", GARBAGE);
    private static final Pattern NOTE_IN_FILE_NAME = Pattern.compile("note-[123]");

    private final String defaultFixture;

    public FixtureExtractor(@Value("${extractor.fixture.default:note-1}") String defaultFixture) {
        this.defaultFixture = defaultFixture;
    }

    @Override
    public String extract(ExtractionRequest request) throws IOException {
        String fixture = choose(request.fixtureHeader(), request.fileName());
        if (GARBAGE.equals(fixture)) {
            return GARBAGE_TEXT;
        }
        return new ClassPathResource("fixtures/" + fixture + ".extracted.json").getContentAsString(StandardCharsets.UTF_8);
    }

    /** Order: X-Fixture header, then a note-N in the upload's file name, then the configured default. */
    String choose(String header, String fileName) {
        if (header != null && KNOWN.contains(header.trim().toLowerCase(Locale.ROOT))) {
            return header.trim().toLowerCase(Locale.ROOT);
        }
        if (fileName != null) {
            Matcher found = NOTE_IN_FILE_NAME.matcher(fileName.toLowerCase(Locale.ROOT));
            if (found.find()) {
                return found.group();
            }
        }
        return defaultFixture;
    }
}
