/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

/**
 * Interface for elements that want to observe computed values from other elements
 * without polluting the main simulation system
 */
public interface ComputedValueObserver {
    /**
     * Called when a computed value is updated
     * @param source The element that computed the value
     * @param key Identifier for the computed value
     * @param value The computed value
     */
    void onComputedValueChanged(CircuitElm source, String key, double value);
    
    /**
     * Get the element that implements this observer
     */
    CircuitElm getObserverElement();
}