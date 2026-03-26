package com.lushprojects.circuitjs1.client.io.sfcr;

import java.util.Collections;
import java.util.List;

public class ParseResult {
    private final int nextIndex;
    private final List<ParseWarning> warnings;

    public ParseResult(int nextIndex, List<ParseWarning> warnings) {
        this.nextIndex = nextIndex;
        this.warnings = (warnings == null) ? Collections.<ParseWarning>emptyList() : warnings;
    }

    public static ParseResult next(int nextIndex) {
        return new ParseResult(nextIndex, Collections.<ParseWarning>emptyList());
    }

    public int getNextIndex() {
        return nextIndex;
    }

    public List<ParseWarning> getWarnings() {
        return warnings;
    }
}
