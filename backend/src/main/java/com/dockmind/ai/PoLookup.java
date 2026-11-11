package com.dockmind.ai;

import com.dockmind.ai.Extractor.OpenPo;
import java.util.Optional;

/** Finds one open PO by the number written on a note. Backed by an indexed query, never a full list. */
@FunctionalInterface
public interface PoLookup {
    Optional<OpenPo> findOpen(String poNumber);
}
