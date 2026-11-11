package com.dockmind.domain;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Turns a PO number as written on a note into the exact values to look up, so the lookup can use the index. */
public final class PoNumbers {
    private static final Pattern DIGITS_AFTER_PO = Pattern.compile("^(?:P[^A-Z0-9]*O)?[^A-Z0-9]*(\\d{1,12})$");

    private PoNumbers() {
    }

    /** "po 4502", "PO4502" and "4502" all give "PO-4502". The value as written is tried too. */
    public static List<String> lookupKeys(String written) {
        if (written == null || written.isBlank()) {
            return List.of();
        }
        String upper = written.trim().toUpperCase(Locale.ROOT);
        Set<String> keys = new LinkedHashSet<>();
        keys.add(upper);
        Matcher digits = DIGITS_AFTER_PO.matcher(upper);
        if (digits.matches()) {
            keys.add("PO-" + digits.group(1));
        }
        return List.copyOf(keys);
    }
}
