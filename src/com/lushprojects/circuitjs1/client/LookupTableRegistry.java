package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Runtime registry for SFCR lookup tables used by Expr.lookup(...).
 */
class LookupTableRegistry {

    static class LookupTableSnapshot {
        ArrayList<Double> xs = new ArrayList<Double>();
        ArrayList<Double> ys = new ArrayList<Double>();
        String resolvedScope;
    }

    private static HashMap<String, LookupDefinition> globalTables;
    private static HashMap<String, HashMap<String, LookupDefinition>> scopedTables;
    private static HashMap<String, LookupDefinition> defaultByName;

    private static void ensureInitialized() {
        if (globalTables == null) {
            globalTables = new HashMap<String, LookupDefinition>();
        }
        if (scopedTables == null) {
            scopedTables = new HashMap<String, HashMap<String, LookupDefinition>>();
        }
        if (defaultByName == null) {
            defaultByName = new HashMap<String, LookupDefinition>();
        }
    }

    static void clear() {
        ensureInitialized();
        globalTables.clear();
        scopedTables.clear();
        defaultByName.clear();
    }

    /**
     * Register a lookup table from a LookupDefinition. If def.scope is null/empty, the table is
     * treated as global; otherwise it is registered under the given scope.
     */
    static void register(LookupDefinition def) {
        if (def == null || def.name == null || def.xs == null || def.ys == null
                || def.xs.size() != def.ys.size() || def.xs.isEmpty()) {
            return;
        }
        ensureInitialized();

        String normalizedName = SFCRUtil.normalizeVariableName(def.name);
        if (def.scope == null || def.scope.isEmpty()) {
            LookupDefinition stored = copyDefinition(def, normalizedName, null);
            globalTables.put(normalizedName, stored);
            defaultByName.put(normalizedName, stored);
        } else {
            String normalizedScope = SFCRUtil.sanitizeName(def.scope);
            HashMap<String, LookupDefinition> byScope = scopedTables.get(normalizedScope);
            if (byScope == null) {
                byScope = new HashMap<String, LookupDefinition>();
                scopedTables.put(normalizedScope, byScope);
            }
            LookupDefinition stored = copyDefinition(def, normalizedName, normalizedScope);
            byScope.put(normalizedName, stored);
            // Only become default if there is no global/default for that bare name.
            if (!defaultByName.containsKey(normalizedName) && !globalTables.containsKey(normalizedName)) {
                defaultByName.put(normalizedName, stored);
            }
        }
    }

    /** Convenience wrapper — prefer {@link #register(LookupDefinition)} for new code. */
    static void registerGlobal(String tableName, ArrayList<Double> xs, ArrayList<Double> ys) {
        LookupDefinition def = new LookupDefinition();
        def.name = tableName;
        def.xs.addAll(xs);
        def.ys.addAll(ys);
        register(def);
    }

    /** Convenience wrapper — prefer {@link #register(LookupDefinition)} for new code. */
    static void registerScoped(String scopeName, String tableName, ArrayList<Double> xs, ArrayList<Double> ys) {
        LookupDefinition def = new LookupDefinition();
        def.name = tableName;
        def.scope = scopeName;
        def.xs.addAll(xs);
        def.ys.addAll(ys);
        register(def);
    }

    static double evaluate(String lookupName, double x, boolean clamp) {
        LookupDefinition table = getTable(lookupName);
        if (table == null || table.xs.isEmpty()) {
            return 0.0;
        }
        return interpolate(table, x, clamp);
    }

    static LookupTableSnapshot getSnapshot(String scopeName, String tableName) {
        if (tableName == null) {
            return null;
        }
        ensureInitialized();

        String normalizedName = SFCRUtil.normalizeVariableName(tableName);
        if (normalizedName == null || normalizedName.isEmpty()) {
            return null;
        }

        LookupDefinition table = null;
        String resolvedScope = null;

        if (scopeName != null && !scopeName.isEmpty()) {
            String normalizedScope = SFCRUtil.sanitizeName(scopeName);
            HashMap<String, LookupDefinition> byScope = scopedTables.get(normalizedScope);
            if (byScope != null) {
                table = byScope.get(normalizedName);
                if (table != null) {
                    resolvedScope = normalizedScope;
                }
            }
        }

        if (table == null) {
            table = globalTables.get(normalizedName);
        }
        if (table == null) {
            table = defaultByName.get(normalizedName);
        }
        if (table == null || table.xs.isEmpty() || table.xs.size() != table.ys.size()) {
            return null;
        }

        LookupTableSnapshot snapshot = new LookupTableSnapshot();
        snapshot.resolvedScope = resolvedScope;
        for (int i = 0; i < table.xs.size(); i++) {
            snapshot.xs.add(Double.valueOf(table.xs.get(i).doubleValue()));
            snapshot.ys.add(Double.valueOf(table.ys.get(i).doubleValue()));
        }
        return snapshot;
    }

    private static LookupDefinition getTable(String lookupName) {
        if (lookupName == null) {
            return null;
        }
        ensureInitialized();

        String raw = lookupName.trim();
        if (raw.isEmpty()) {
            return null;
        }

        int scopeSep = raw.indexOf(':');
        if (scopeSep > 0 && scopeSep < raw.length() - 1) {
            String scope = SFCRUtil.sanitizeName(raw.substring(0, scopeSep));
            String name = SFCRUtil.normalizeVariableName(raw.substring(scopeSep + 1));
            HashMap<String, LookupDefinition> byScope = scopedTables.get(scope);
            if (byScope != null) {
                LookupDefinition scoped = byScope.get(name);
                if (scoped != null) {
                    return scoped;
                }
            }
            // Fall through to unscoped resolution for safety.
            raw = raw.substring(scopeSep + 1);
        }

        String normalized = SFCRUtil.normalizeVariableName(raw);
        LookupDefinition global = globalTables.get(normalized);
        if (global != null) {
            return global;
        }
        return defaultByName.get(normalized);
    }

    /**
     * Serialize all registered lookup tables as {@code % lookup} lines suitable for
     * embedding in a classic circuit dump. Each line has the form:
     * {@code % lookup name [scope=X] x1,y1 x2,y2 ...}
     */
    static String dumpAll() {
        ensureInitialized();
        StringBuilder sb = new StringBuilder();
        for (LookupDefinition table : globalTables.values()) {
            appendDumpLine(sb, table, null);
        }
        for (java.util.Map.Entry<String, HashMap<String, LookupDefinition>> entry : scopedTables.entrySet()) {
            String scope = entry.getKey();
            for (LookupDefinition table : entry.getValue().values()) {
                appendDumpLine(sb, table, scope);
            }
        }
        return sb.toString();
    }

    private static void appendDumpLine(StringBuilder sb, LookupDefinition table, String scope) {
        if (table == null || table.xs.isEmpty()) {
            return;
        }
        sb.append("% lookup ").append(table.name);
        if (scope != null && !scope.isEmpty()) {
            sb.append(" scope=").append(scope);
        }
        for (int i = 0; i < table.xs.size(); i++) {
            sb.append(" ").append(table.xs.get(i).doubleValue())
              .append(",").append(table.ys.get(i).doubleValue());
        }
        sb.append("\n");
    }

    private static LookupDefinition copyDefinition(LookupDefinition src, String normalizedName, String normalizedScope) {
        LookupDefinition copy = new LookupDefinition();
        copy.name = normalizedName;
        copy.scope = normalizedScope;
        for (int i = 0; i < src.xs.size(); i++) {
            copy.xs.add(src.xs.get(i));
            copy.ys.add(src.ys.get(i));
        }
        copy.comments.addAll(src.comments);
        return copy;
    }

    private static double interpolate(LookupDefinition table, double x, boolean clamp) {
        int n = table.xs.size();
        if (n == 1) {
            return table.ys.get(0).doubleValue();
        }

        double x0 = table.xs.get(0).doubleValue();
        double y0 = table.ys.get(0).doubleValue();
        double x1 = table.xs.get(1).doubleValue();
        double y1 = table.ys.get(1).doubleValue();

        if (x < x0) {
            if (clamp) {
                return y0;
            }
            return linearInterpolate(x, x0, y0, x1, y1);
        }

        int i = 2;
        while (true) {
            if (x < x1) {
                return linearInterpolate(x, x0, y0, x1, y1);
            }
            if (i >= n) {
                break;
            }
            x0 = x1;
            y0 = y1;
            x1 = table.xs.get(i).doubleValue();
            y1 = table.ys.get(i).doubleValue();
            i++;
        }

        if (clamp) {
            return y1;
        }
        return linearInterpolate(x, x0, y0, x1, y1);
    }

    private static double linearInterpolate(double x, double x0, double y0, double x1, double y1) {
        double dx = x1 - x0;
        if (Math.abs(dx) < 1e-12) {
            return y0;
        }
        return y0 + (x - x0) * (y1 - y0) / dx;
    }
}
