package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.io.SFCRParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SFCRParseContext {
    private final SFCRParser parser;
    private final ArrayList<ParseWarning> warnings = new ArrayList<ParseWarning>();

    public SFCRParseContext(SFCRParser parser) {
        this.parser = parser;
    }

    public SFCRParser getParser() {
        return parser;
    }

    public void addWarning(int line, String message) {
        warnings.add(new ParseWarning(line, message));
    }

    public List<ParseWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
