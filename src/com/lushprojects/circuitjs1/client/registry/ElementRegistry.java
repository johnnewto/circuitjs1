package com.lushprojects.circuitjs1.client.registry;

import java.util.HashMap;
import java.util.Map;
import java.util.TreeMap;

import com.lushprojects.circuitjs1.client.CircuitElm;
import com.lushprojects.circuitjs1.client.util.StringTokenizer;
import com.lushprojects.circuitjs1.client.elements.electronics.misc.CustomCompositeElm;

public final class ElementRegistry {
    public interface DumpFactory {
        CircuitElm create(int x1, int y1, int x2, int y2, int f, StringTokenizer st);
    }

    public interface NameFactory {
        CircuitElm create(int x1, int y1);
    }

    public static final class Entry {
        final String classKey;
        public final String canonicalClassKey;
        public final ElementCategory category;
        public final boolean alias;
        final boolean inferred;
        public final String deprecationMessage;
        final NameFactory nameFactory;
        final DumpFactory dumpFactory;

        Entry(String classKey, String canonicalClassKey, ElementCategory category,
              boolean alias, boolean inferred, String deprecationMessage,
              NameFactory nameFactory, DumpFactory dumpFactory) {
            this.classKey = classKey;
            this.canonicalClassKey = canonicalClassKey;
            this.category = category;
            this.alias = alias;
            this.inferred = inferred;
            this.deprecationMessage = deprecationMessage;
            this.nameFactory = nameFactory;
            this.dumpFactory = dumpFactory;
        }
    }

    public static final class NameLookupResult {
        public final CircuitElm element;
        public final Entry entry;

        NameLookupResult(CircuitElm element, Entry entry) {
            this.element = element;
            this.entry = entry;
        }
    }

    private static final Map<Integer, Entry> dumpTypeMap = new HashMap<Integer, Entry>();
    private static final Map<String, Entry> classNameMap = new HashMap<String, Entry>();
    private static final Map<String, ElementCategory> inferredClassKeyCategories = new HashMap<String, ElementCategory>();
    private static final Map<Integer, ElementCategory> inferredDumpTypeCategories = new HashMap<Integer, ElementCategory>();
    private static boolean initialized;

    private ElementRegistry() {
    }

    public static synchronized void ensureInitialized() {
        if (initialized) {
            return;
        }
        ElementRegistryBootstrap.registerAll();
        initialized = true;
    }

    public static synchronized void resetForTests() {
        dumpTypeMap.clear();
        classNameMap.clear();
        inferredClassKeyCategories.clear();
        inferredDumpTypeCategories.clear();
        initialized = false;
    }

    public static synchronized void registerElement(int dumpType, String classKey, ElementCategory category,
                                             NameFactory nameFactory, DumpFactory dumpFactory) {
        Entry entry = new Entry(classKey, classKey, category, false, false, null, nameFactory, dumpFactory);
        classNameMap.put(classKey, entry);
        if (dumpFactory != null) {
            dumpTypeMap.put(dumpType, entry);
        }
    }

    public static synchronized void registerAlias(String aliasKey, String canonicalClassKey, ElementCategory category,
                                           String deprecationMessage) {
        Entry canonical = classNameMap.get(canonicalClassKey);
        if (canonical == null) {
            throw new IllegalStateException("Canonical class key not found for alias: " + canonicalClassKey);
        }
        Entry alias = new Entry(aliasKey, canonicalClassKey, category, true, false, deprecationMessage,
                canonical.nameFactory, canonical.dumpFactory);
        classNameMap.put(aliasKey, alias);
    }

    public static synchronized void registerDumpAlias(int dumpType, String canonicalClassKey, String note) {
        Entry canonical = classNameMap.get(canonicalClassKey);
        if (canonical == null) {
            throw new IllegalStateException("Canonical class key not found for dump alias: " + canonicalClassKey);
        }
        Entry alias = new Entry(canonical.classKey, canonical.canonicalClassKey, canonical.category,
                canonical.alias, false, note, canonical.nameFactory, canonical.dumpFactory);
        dumpTypeMap.put(dumpType, alias);
    }

    public static CircuitElm createFromDumpType(int dumpType, int x1, int y1, int x2, int y2, int f, StringTokenizer st) {
        ensureInitialized();
        Entry entry = dumpTypeMap.get(dumpType);
        if (entry != null && entry.dumpFactory != null) {
            return entry.dumpFactory.create(x1, y1, x2, y2, f, st);
        }

        CircuitElm legacyElement = ElementLegacyFactory.createCeLegacy(dumpType, x1, y1, x2, y2, f, st);
        if (legacyElement == null) {
            return null;
        }

        synchronized (ElementRegistry.class) {
            if (!dumpTypeMap.containsKey(dumpType)) {
                final int capturedDumpType = dumpType;
                final String resolvedClassKey = legacyElement.getClass().getSimpleName();
                final ElementCategory inferredCategory = inferCategory(resolvedClassKey);
                Entry dynamic = new Entry(resolvedClassKey, resolvedClassKey, inferredCategory,
                        false, true, null,
                        null,
                        new DumpFactory() {
                            @Override
                            public CircuitElm create(int lx1, int ly1, int lx2, int ly2, int lf, StringTokenizer lst) {
                                return ElementLegacyFactory.createCeLegacy(capturedDumpType, lx1, ly1, lx2, ly2, lf, lst);
                            }
                        });
                dumpTypeMap.put(dumpType, dynamic);
                inferredDumpTypeCategories.put(dumpType, inferredCategory);
            }
        }

        return legacyElement;
    }

    public static NameLookupResult createFromClassKey(String classKey, int x1, int y1) {
        ensureInitialized();
        if (classKey == null) {
            return null;
        }

        if (classKey.startsWith("CustomCompositeElm:")) {
            int ix = classKey.indexOf(':') + 1;
            String name = classKey.substring(ix);
            Entry synthetic = new Entry(classKey, "CustomCompositeElm", ElementCategory.UI_SUPPORT,
                    false, false, null, null, null);
            return new NameLookupResult(new CustomCompositeElm(x1, y1, name), synthetic);
        }

        Entry entry = classNameMap.get(classKey);
        if (entry != null && entry.nameFactory != null) {
            return new NameLookupResult(entry.nameFactory.create(x1, y1), entry);
        }

        CircuitElm legacyElement = ElementLegacyFactory.constructElementLegacy(classKey, x1, y1);
        if (legacyElement == null) {
            return null;
        }

        synchronized (ElementRegistry.class) {
            Entry cached = classNameMap.get(classKey);
            if (cached == null) {
                final String capturedKey = classKey;
                final ElementCategory inferredCategory = inferCategory(capturedKey);
                Entry dynamic = new Entry(capturedKey, capturedKey, inferredCategory,
                        false, true, null,
                        new NameFactory() {
                            @Override
                            public CircuitElm create(int lx1, int ly1) {
                                return ElementLegacyFactory.constructElementLegacy(capturedKey, lx1, ly1);
                            }
                        },
                        null);
                classNameMap.put(capturedKey, dynamic);
                inferredClassKeyCategories.put(capturedKey, inferredCategory);
                cached = dynamic;
            }
            return new NameLookupResult(legacyElement, cached);
        }
    }

    private static ElementCategory inferCategory(String classKey) {
        if (classKey == null) {
            return ElementCategory.OTHER;
        }

        if (classKey.startsWith("SFC") || classKey.contains("Table") || classKey.contains("Stock")
                || classKey.contains("Flow") || classKey.contains("Scenario")
                || classKey.contains("ComputedValue")) {
            return ElementCategory.ECONOMICS;
        }

        if (classKey.contains("Multiply") || classKey.contains("Divide") || classKey.contains("Adder")
                || classKey.contains("Subtract") || classKey.contains("Integrator")
                || classKey.contains("Differentiator") || classKey.contains("ODE")
                || classKey.equals("PercentElm")) {
            return ElementCategory.MATH;
        }

        if (classKey.contains("Scope") || classKey.contains("Stop") || classKey.contains("Action")
                || classKey.contains("Viewport") || classKey.contains("PieChart") || classKey.contains("Text")
                || classKey.contains("Line") || classKey.contains("Box") || classKey.contains("Probe")
                || classKey.contains("Output") || classKey.contains("Display") || classKey.contains("Recorder")) {
            return ElementCategory.UI_SUPPORT;
        }

        return ElementCategory.ELECTRONICS;
    }

    public static Entry getEntryByClassKey(String classKey) {
        ensureInitialized();
        return classNameMap.get(classKey);
    }

    public static Entry getEntryByDumpType(int dumpType) {
        ensureInitialized();
        return dumpTypeMap.get(dumpType);
    }

    public static synchronized Map<String, ElementCategory> getInferredClassKeyCategoriesSnapshot() {
        return new TreeMap<String, ElementCategory>(inferredClassKeyCategories);
    }

    public static synchronized Map<Integer, ElementCategory> getInferredDumpTypeCategoriesSnapshot() {
        return new TreeMap<Integer, ElementCategory>(inferredDumpTypeCategories);
    }

    public static synchronized String buildInferredUsageReport() {
        StringBuilder sb = new StringBuilder();
        sb.append("Inferred class keys: ");
        sb.append(new TreeMap<String, ElementCategory>(inferredClassKeyCategories));
        sb.append(" | Inferred dump types: ");
        sb.append(new TreeMap<Integer, ElementCategory>(inferredDumpTypeCategories));
        return sb.toString();
    }
}
