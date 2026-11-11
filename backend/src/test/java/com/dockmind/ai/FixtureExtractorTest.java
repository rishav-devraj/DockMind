package com.dockmind.ai;

import static org.assertj.core.api.Assertions.assertThat;

import com.dockmind.ai.Extractor.ExtractionRequest;
import org.junit.jupiter.api.Test;

class FixtureExtractorTest {
    private final FixtureExtractor extractor = new FixtureExtractor("note-1");

    @Test
    void headerWinsOverFileName() {
        assertThat(extractor.choose("note-3", "note-2-short.png")).isEqualTo("note-3");
    }

    @Test
    void fileNameIsUsedWithoutHeader() {
        assertThat(extractor.choose(null, "note-2-short.png")).isEqualTo("note-2");
    }

    @Test
    void unknownHeaderFallsThrough() {
        assertThat(extractor.choose("note-99", "note-2-short.png")).isEqualTo("note-2");
    }

    @Test
    void configuredDefaultIsLast() {
        assertThat(extractor.choose(null, "IMG_0042.jpg")).isEqualTo("note-1");
    }

    @Test
    void garbageReturnsUnparseableText() throws Exception {
        String raw = extractor.extract(new ExtractionRequest(new byte[0], "image/png", "x.png", "garbage"));
        assertThat(raw).doesNotContain("{");
    }

    @Test
    void returnsKitFixture() throws Exception {
        String raw = extractor.extract(new ExtractionRequest(new byte[0], "image/png", "x.png", "note-2"));
        assertThat(raw).contains("PO-4502").contains("Scallops");
    }
}
