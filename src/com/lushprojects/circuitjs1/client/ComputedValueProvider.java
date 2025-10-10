/*    
    Copyright (C) Paul Falstad and Iain Sharp
    
    This file is part of CircuitJS1.
*/

package com.lushprojects.circuitjs1.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Base class for circuit elements that can provide computed values to observers
 * This provides a cleaner alternative to the global LabeledNodeElm computed value system
 */
public abstract class ComputedValueProvider extends CircuitElm {
    
    private Map<String, List<ComputedValueObserver>> observers = new HashMap<>();
    private Map<String, Double> computedValues = new HashMap<>();
    
    public ComputedValueProvider(int xx, int yy) {
        super(xx, yy);
    }
    
    public ComputedValueProvider(int xa, int ya, int xb, int yb, int f) {
        super(xa, ya, xb, yb, f);
    }
    
    /**
     * Register an observer for a specific computed value key
     */
    public void addComputedValueObserver(String key, ComputedValueObserver observer) {
        if (!observers.containsKey(key)) {
            observers.put(key, new ArrayList<ComputedValueObserver>());
        }
        observers.get(key).add(observer);
    }
    
    /**
     * Remove an observer for a specific key
     */
    public void removeComputedValueObserver(String key, ComputedValueObserver observer) {
        List<ComputedValueObserver> keyObservers = observers.get(key);
        if (keyObservers != null) {
            keyObservers.remove(observer);
            if (keyObservers.isEmpty()) {
                observers.remove(key);
            }
        }
    }
    
    /**
     * Set a computed value and notify observers
     */
    protected void setComputedValue(String key, double value) {
        Double oldValue = computedValues.get(key);
        if (oldValue == null || Math.abs(oldValue - value) > 1e-12) {
            computedValues.put(key, value);
            notifyObservers(key, value);
        }
    }
    
    /**
     * Get a computed value
     */
    public Double getComputedValue(String key) {
        return computedValues.get(key);
    }
    
    /**
     * Notify all observers of a value change
     */
    private void notifyObservers(String key, double value) {
        List<ComputedValueObserver> keyObservers = observers.get(key);
        if (keyObservers != null) {
            for (ComputedValueObserver observer : keyObservers) {
                try {
                    observer.onComputedValueChanged(this, key, value);
                } catch (Exception e) {
                    // Don't let observer exceptions break the provider
                    CirSim.console("Error notifying observer: " + e.getMessage());
                }
            }
        }
    }
    
    /**
     * Clear all computed values (called on circuit reset)
     */
    public void clearComputedValues() {
        computedValues.clear();
        // Note: We don't clear observers as they may want to re-register
    }
    
    /**
     * Get all available computed value keys
     */
    public String[] getComputedValueKeys() {
        return computedValues.keySet().toArray(new String[0]);
    }
}