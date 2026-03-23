package com.lushprojects.circuitjs1.client.core;

public interface ConfigProvider {
    boolean isEquationTableMnaMode();
    boolean isSfcrLookupClampDefault();
    double getEquationTableConvergenceTolerance();
}
