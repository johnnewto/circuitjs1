package com.lushprojects.circuitjs1.client;

final class ElementFactoryFacade {
    private ElementFactoryFacade() {
    }

    static CircuitElm createFromDumpType(int dumpType, int x1, int y1, int x2, int y2, int f, StringTokenizer st) {
        CircuitElm registryElement = ElementRegistry.createFromDumpType(dumpType, x1, y1, x2, y2, f, st);
        if (registryElement != null) {
            return registryElement;
        }
        return ElementLegacyFactory.createCeLegacy(dumpType, x1, y1, x2, y2, f, st);
    }

    static CircuitElm constructFromClassKey(String classKey, int x1, int y1) {
        ElementRegistry.NameLookupResult lookupResult = ElementRegistry.createFromClassKey(classKey, x1, y1);
        if (lookupResult != null) {
            if (lookupResult.entry != null && lookupResult.entry.alias && lookupResult.entry.deprecationMessage != null) {
                CirSim.console(lookupResult.entry.deprecationMessage);
            }
            return lookupResult.element;
        }
        return ElementLegacyFactory.constructElementLegacy(classKey, x1, y1);
    }
}
