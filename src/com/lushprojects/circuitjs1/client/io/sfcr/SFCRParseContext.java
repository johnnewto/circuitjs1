package com.lushprojects.circuitjs1.client.io.sfcr;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.elements.ActionScheduler;
import com.lushprojects.circuitjs1.client.elements.misc.ActionTimeElm;
import com.lushprojects.circuitjs1.client.io.LookupDefinition;
import com.lushprojects.circuitjs1.client.io.SFCRParser;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Vector;

public class SFCRParseContext {
    private final SFCRParser parser;
    private final ArrayList<ParseWarning> warnings = new ArrayList<ParseWarning>();

    public SFCRParseContext(SFCRParser parser) {
        this.parser = parser;
    }

    public SFCRParser.BlockHeaderInfo parseBlockHeader(String line, String keyword) {
        return parser.parseBlockHeaderForHandler(line, keyword);
    }

    public boolean parseBoolean(String text, boolean defaultValue) {
        return parser.parseBooleanForHandler(text, defaultValue);
    }

    public String[] parseTableRow(String line) {
        return parser.parseTableRowForHandler(line);
    }

    public String unescapeTableCell(String text) {
        return parser.unescapeTableCellForHandler(text);
    }

    public ActionScheduler getActionScheduler() {
        return parser.getActionSchedulerForHandler();
    }

    public ActionTimeElm findActionTimeElm() {
        return parser.findActionTimeElmForHandler();
    }

    public CirSim getSim() {
        return parser.getSimForHandler();
    }

    public void addCreatedElement(CircuitElm elm) {
        parser.addCreatedElementForHandler(elm);
    }

    public void setActionElementFromActionBlock(boolean value) {
        parser.setActionElementFromActionBlockForHandler(value);
    }

    public void parseInitInline(String params) {
        parser.parseInitInlineForHandler(params);
    }

    public void registerInitSetting(String key, String value) {
        parser.registerInitSettingForHandler(key, value);
    }

    public LookupDefinition parseLookupHeader(String headerLine) {
        return parser.parseLookupHeaderForHandler(headerLine);
    }

    public SFCRParser.LookupPoint parseLookupPoint(String line) {
        return parser.parseLookupPointForHandler(line);
    }

    public boolean isStrictlyIncreasing(ArrayList<Double> xs) {
        return parser.isStrictlyIncreasingForHandler(xs);
    }

    public void registerLookupTable(LookupDefinition table) {
        parser.registerLookupTableForHandler(table);
    }

    public void registerHint(String varName, String description) {
        parser.registerHintForHandler(varName, description);
    }

    public String normalizeVariableName(String rawName) {
        return parser.normalizeVariableNameForHandler(rawName);
    }

    public String normalizeExpression(String rawExpr) {
        return parser.normalizeExpressionForHandler(rawExpr);
    }

    public String rewriteLookupCalls(String expr, String equationScope) {
        return parser.rewriteLookupCallsForHandler(expr, equationScope);
    }

    public String[] splitDifferenceLeftAlias(String left) {
        return parser.splitDifferenceLeftAliasForHandler(left);
    }

    public int getCurrentX() {
        return parser.getCurrentXForHandler();
    }

    public int getCurrentY() {
        return parser.getCurrentYForHandler();
    }

    public void setCurrentPosition(int x, int y) {
        parser.setCurrentPositionForHandler(x, y);
    }

    public int getElementSpacing() {
        return parser.getElementSpacingForHandler();
    }

    public boolean hasHint(String varName) {
        return parser.hasHintForHandler(varName);
    }

    public void createEquationTable(String name, ArrayList<String> outputNames,
                                    ArrayList<String> equations, ArrayList<Integer> outputModes,
                                    ArrayList<String> targetNodeNames, ArrayList<String> sliderVarNames,
                                    ArrayList<Double> sliderValues, ArrayList<String> initialEquations) {
        parser.createEquationTableForHandler(name, outputNames, equations, outputModes,
            targetNodeNames, sliderVarNames, sliderValues, initialEquations);
    }

    public void createMatrixTable(String name, ArrayList<String> columnNames, ArrayList<String> rowNames,
                                  ArrayList<String[]> tableRows, String matrixType,
                                  Boolean showInitialValuesOverride, Boolean showFlowValuesOverride,
                                  Boolean useBackwardEulerOverride) {
        parser.createMatrixTableForHandler(name, columnNames, rowNames, tableRows, matrixType,
            showInitialValuesOverride, showFlowValuesOverride, useBackwardEulerOverride);
    }

    public boolean isActionElementFromActionBlock() {
        return parser.isActionElementFromActionBlockForHandler();
    }

    public void addRawCircuitLine(String line) {
        parser.addRawCircuitLineForHandler(line);
    }

    public boolean hasPendingResult() {
        return parser.hasPendingResultForHandler();
    }

    public void addBlockDump(String blockType, String blockName, String dumpString) {
        parser.addBlockDumpForHandler(blockType, blockName, dumpString);
    }

    public String parseLookupDumpLineFromCircuit(String line) {
        return parser.parseLookupDumpLineFromCircuitForHandler(line);
    }

    public String inferBlockTypeFromCircuitDumpLine(String line) {
        return parser.inferBlockTypeFromCircuitDumpLineForHandler(line);
    }

    public boolean looksLikeScopeBlock(String[] lines, int startIndex) {
        return parser.looksLikeScopeBlockForHandler(lines, startIndex);
    }

    public void addScopeVariable(String varName) {
        parser.addScopeVariableForHandler(varName);
    }

    public void addScopeBlock(SFCRParser.ScopeBlockSpec spec) {
        parser.addScopeBlockForHandler(spec);
    }

    public void setInfoContent(String content) {
        parser.setInfoContentForHandler(content);
    }

    public SFCRParser.RStyleBlockMetadata consumeRStyleMetadataFromComments(Vector<String> pendingComments) {
        return parser.consumeRStyleMetadataFromCommentsForHandler(pendingComments);
    }

    public String extractRStyleAssignmentName(String block, String defaultName) {
        return parser.extractRStyleAssignmentNameForHandler(block, defaultName);
    }

    public void parseRStyleMatrix(String block, SFCRParser.RStyleBlockMetadata metadata) {
        parser.parseRStyleMatrixForHandler(block, metadata);
    }

    public void parseRStyleEquations(String block, SFCRParser.RStyleBlockMetadata metadata) {
        parser.parseRStyleEquationsForHandler(block, metadata);
    }

    public void storePendingMatrixBlockComments(String blockName, Vector<String> pendingComments) {
        parser.storePendingMatrixBlockCommentsForHandler(blockName, pendingComments);
    }

    public void storePendingEquationsBlockComments(String blockName, Vector<String> pendingComments) {
        parser.storePendingEquationsBlockCommentsForHandler(blockName, pendingComments);
    }

    public void addWarning(int line, String message) {
        warnings.add(new ParseWarning(line, message));
    }

    public List<ParseWarning> getWarnings() {
        return Collections.unmodifiableList(warnings);
    }
}
