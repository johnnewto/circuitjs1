package com.lushprojects.circuitjs1.client;

public class ScopePropertiesDialog extends ScopePropertiesDialogCore {

    public ScopePropertiesDialog(CirSim sim, Scope scope) {
        super(sim, scope);
    }

    public static double nextHighestScale(double d) {
        return ScopePropertiesDialogCore.nextHighestScale(d);
    }
}
