package com.lushprojects.circuitjs1.client;

import com.lushprojects.circuitjs1.client.economics.*;
import com.lushprojects.circuitjs1.client.electronics.digital.*;
import com.lushprojects.circuitjs1.client.electronics.passives.*;
import com.lushprojects.circuitjs1.client.electronics.sources.*;

import com.lushprojects.circuitjs1.client.math.*;
import com.lushprojects.circuitjs1.client.registry.ElementCategory;
import com.lushprojects.circuitjs1.client.registry.ElementRegistry;

public final class ElementRegistryBootstrap {
    private ElementRegistryBootstrap() {
    }

    public static void registerAll() {
        registerElectronics();
        registerEconomics();
        registerMath();
        registerUiSupport();
        registerLegacyAliases();
    }

    private static void register(int dumpType, String classKey, ElementCategory category,
                                 ElementRegistry.NameFactory nameFactory,
                                 ElementRegistry.DumpFactory dumpFactory) {
        ElementRegistry.registerElement(dumpType, classKey, category, nameFactory, dumpFactory);
    }

    private static void registerElectronics() {
        register('r', "ResistorElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new ResistorElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ResistorElm(x1, y1, x2, y2, f, st));
        register('w', "WireElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new WireElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new WireElm(x1, y1, x2, y2, f, st));
        register('g', "GroundElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new GroundElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new GroundElm(x1, y1, x2, y2, f, st));
        register('c', "CapacitorElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new CapacitorElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new CapacitorElm(x1, y1, x2, y2, f, st));
        register('l', "InductorElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new InductorElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new InductorElm(x1, y1, x2, y2, f, st));
        register('d', "DiodeElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new DiodeElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new DiodeElm(x1, y1, x2, y2, f, st));
        register('z', "ZenerElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new ZenerElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ZenerElm(x1, y1, x2, y2, f, st));
        register(163, "RingCounterElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new RingCounterElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new RingCounterElm(x1, y1, x2, y2, f, st));
        register(208, "CustomLogicElm", ElementCategory.ELECTRONICS,
            (x1, y1) -> new CustomLogicElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new CustomLogicElm(x1, y1, x2, y2, f, st));
    }

    private static void registerEconomics() {
        register(236, "ScenarioElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new ScenarioElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ScenarioElm(x1, y1, x2, y2, f, st));
        register(253, "TableElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new TableElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new TableElm(x1, y1, x2, y2, f, st));
        register(254, "CurrentTransactionsMatrixElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new CurrentTransactionsMatrixElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new CurrentTransactionsMatrixElm(x1, y1, x2, y2, f, st));
        register(255, "GodlyTableElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new GodlyTableElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new GodlyTableElm(x1, y1, x2, y2, f, st));
        register(256, "TableVoltageElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new TableVoltageElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new TableVoltageElm(x1, y1, x2, y2, f, st));
        register(262, "EquationElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new EquationElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new EquationElm(x1, y1, x2, y2, f, st));
        register(265, "SFCTableElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new SFCTableElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new SFCTableElm(x1, y1, x2, y2, f, st));
        register(266, "EquationTableElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new EquationTableElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new EquationTableElm(x1, y1, x2, y2, f, st));
        register(267, "ComputedValueSourceElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new ComputedValueSourceElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ComputedValueSourceElm(x1, y1, x2, y2, f, st));
        register(268, "SFCStockElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new SFCStockElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new SFCStockElm(x1, y1, x2, y2, f, st));
        register(269, "SFCFlowElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new SFCFlowElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new SFCFlowElm(x1, y1, x2, y2, f, st));
        register(450, "StockMasterElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new StockMasterElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new StockMasterElm(x1, y1, x2, y2, f, st));
        register(451, "FlowsMasterElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new FlowsMasterElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new FlowsMasterElm(x1, y1, x2, y2, f, st));
        register(466, "SFCSankeyElm", ElementCategory.ECONOMICS,
            (x1, y1) -> new SFCSankeyElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new SFCSankeyElm(x1, y1, x2, y2, f, st));
        ElementRegistry.registerDumpAlias(270, "SFCTableElm", "Legacy dump type for SFCKclNodeTable compatibility");
    }

    private static void registerMath() {
        register('P', "PercentElm", ElementCategory.MATH,
            (x1, y1) -> new PercentElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new PercentElm(x1, y1, x2, y2, f, st));
        register(250, "MultiplyElm", ElementCategory.MATH,
            (x1, y1) -> new MultiplyElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new MultiplyElm(x1, y1, x2, y2, f, st));
        register(251, "AdderElm", ElementCategory.MATH,
            (x1, y1) -> new AdderElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new AdderElm(x1, y1, x2, y2, f, st));
        register(252, "SubtracterElm", ElementCategory.MATH,
            (x1, y1) -> new SubtracterElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new SubtracterElm(x1, y1, x2, y2, f, st));
        register(257, "DividerElm", ElementCategory.MATH,
            (x1, y1) -> new DividerElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new DividerElm(x1, y1, x2, y2, f, st));
        register(258, "MultiplyConstElm", ElementCategory.MATH,
            (x1, y1) -> new MultiplyConstElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new MultiplyConstElm(x1, y1, x2, y2, f, st));
        register(259, "DifferentiatorElm", ElementCategory.MATH,
            (x1, y1) -> new DifferentiatorElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new DifferentiatorElm(x1, y1, x2, y2, f, st));
        register(260, "IntegratorElm", ElementCategory.MATH,
            (x1, y1) -> new IntegratorElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new IntegratorElm(x1, y1, x2, y2, f, st));
        register(261, "ODEElm", ElementCategory.MATH,
            (x1, y1) -> new ODEElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ODEElm(x1, y1, x2, y2, f, st));
        register(264, "DivideConstElm", ElementCategory.MATH,
            (x1, y1) -> new DivideConstElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new DivideConstElm(x1, y1, x2, y2, f, st));
        }

        private static void registerUiSupport() {
        register(217, "PieChartElm", ElementCategory.UI_SUPPORT,
            (x1, y1) -> new PieChartElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new PieChartElm(x1, y1, x2, y2, f, st));
        register(263, "ViewportElm", ElementCategory.UI_SUPPORT,
            (x1, y1) -> new ViewportElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ViewportElm(x1, y1, x2, y2, f, st));
        register(431, "StopTimeElm", ElementCategory.UI_SUPPORT,
            (x1, y1) -> new StopTimeElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new StopTimeElm(x1, y1, x2, y2, f, st));
        register(432, "ActionTimeElm", ElementCategory.UI_SUPPORT,
            (x1, y1) -> new ActionTimeElm(x1, y1),
            (x1, y1, x2, y2, f, st) -> new ActionTimeElm(x1, y1, x2, y2, f, st));
    }

    private static void registerLegacyAliases() {
        ElementRegistry.registerAlias("DecadeElm", "RingCounterElm", ElementCategory.ELECTRONICS,
                "DecadeElm is deprecated; use RingCounterElm instead");
        ElementRegistry.registerAlias("UserDefinedLogicElm", "CustomLogicElm", ElementCategory.ELECTRONICS,
                "UserDefinedLogicElm is deprecated; use CustomLogicElm instead");
        ElementRegistry.registerAlias("SFCKclNodeTableElm", "SFCTableElm", ElementCategory.ECONOMICS,
                "SFCKclNodeTableElm is deprecated; use SFCTableElm instead");
    }
}
