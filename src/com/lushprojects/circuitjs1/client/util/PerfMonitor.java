package com.lushprojects.circuitjs1.client.util;

import java.util.*;

import jsinterop.annotations.JsMethod;
import jsinterop.annotations.JsPackage;
import jsinterop.annotations.JsProperty;
import jsinterop.annotations.JsType;

public class PerfMonitor {

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Performance")
    private static class PerformanceLike {
        @JsMethod native double now();
    }

    @JsType(isNative = true, namespace = JsPackage.GLOBAL, name = "Window")
    private static class WindowLike {
        @JsProperty native PerformanceLike getPerformance();
    }

    @JsProperty(namespace = JsPackage.GLOBAL, name = "window")
    private static native WindowLike getWindow();

    @JsMethod(namespace = JsPackage.GLOBAL, name = "Date.now")
    private static native double dateNow();
    
    private String rootCtxName;
    private PerfEntry rootCtx;
    private PerfEntry ctx;
    
    public PerfMonitor() {
    
    }

    public void startContext(String name) {
        PerfEntry newEntry = startNewEntry(ctx);
        if (ctx == null) {
            ctx = newEntry;
            if (rootCtx == null) {
                rootCtxName = name;
                rootCtx = ctx;
            }
        } else {
            if (ctx.AddChild(name, newEntry)) {
                ctx = newEntry;
            }
        }
    }
    
    public void stopContext() {
        if (ctx != null) {
            ctx.endTime = getTime();
            ctx.length = ctx.endTime - ctx.startTime;
            ctx = ctx.parent;
        }
    }
    
    private PerfEntry startNewEntry(PerfEntry parent) {
        PerfEntry newEntry = new PerfEntry(parent);
        newEntry.startTime = getTime();
        return newEntry;
    }
    
    public static StringBuilder buildString(PerfMonitor mon) {
        StringBuilder sb = new StringBuilder();
        buildStringInternal(sb, mon.rootCtxName, mon.rootCtx, 0);
        return sb;
    } 
    
    private static void buildStringInternal(StringBuilder sb, String name, PerfEntry entry, int depth) {
        for (int x = 0; x < depth; x++) {
            sb.append("-");
        }
        sb.append(name);
        sb.append(": ");
        sb.append(entry.length);
        sb.append("\n");
        Set<String> keys = entry.children.keySet();
        for (String key : keys){
            PerfEntry child = entry.children.get(key);
            buildStringInternal(sb, key, child, depth + 1);
        }
    }
    
    private static float getTime() {
        WindowLike window = getWindow();
        PerformanceLike performance = (window == null) ? null : window.getPerformance();
        if (performance != null) {
            return (float) performance.now();
        }
        return (float) dateNow();
    }

    class PerfEntry {
    
        PerfEntry parent;
        HashMap<String, PerfEntry> children;
            
        float startTime;
        float endTime;
        float length;
        
        PerfEntry(PerfEntry p) {
            parent = p;
            children = new HashMap<String, PerfEntry>();
        }
        
        boolean AddChild(String name, PerfEntry entry) {
            if (!children.containsKey(name)) {
                children.put(name, entry);
                return true;
            }
            return false;
        }
        
        public PerfEntry GetChild(String name) {
            if (children.containsKey(name)) {
                return children.get(name);
            }
            return null;
        }
    }
    
}
