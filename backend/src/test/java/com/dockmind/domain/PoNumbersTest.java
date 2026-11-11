package com.dockmind.domain;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PoNumbersTest {
    @ParameterizedTest
    @ValueSource(strings = {"PO-4502", "po-4502", "PO 4502", "PO4502", "P.O. 4502", "4502", " po_4502 "})
    void writtenFormsLookUpTheCanonicalNumber(String written) {
        assertThat(PoNumbers.lookupKeys(written)).contains("PO-4502");
    }

    @Test
    void anUnusualNumberIsStillTriedAsWritten() {
        assertThat(PoNumbers.lookupKeys("ORD/77-A")).containsExactly("ORD/77-A");
    }

    @Test
    void blankGivesNothingToLookUp() {
        assertThat(PoNumbers.lookupKeys(" ")).isEmpty();
        assertThat(PoNumbers.lookupKeys(null)).isEmpty();
    }
}
