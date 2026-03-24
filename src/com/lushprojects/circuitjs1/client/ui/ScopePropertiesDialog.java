package com.lushprojects.circuitjs1.client.ui;

import com.lushprojects.circuitjs1.client.CirSim;
import com.lushprojects.circuitjs1.client.Scope;
import com.lushprojects.circuitjs1.client.ScopePropertiesDialogCore;

public class ScopePropertiesDialog extends ScopePropertiesDialogCore {

    public ScopePropertiesDialog(CirSim sim, Scope scope) {
        super(sim, scope);
    }

    public static double nextHighestScale(double d) {
        return ScopePropertiesDialogCore.nextHighestScale(d);
    }
}
