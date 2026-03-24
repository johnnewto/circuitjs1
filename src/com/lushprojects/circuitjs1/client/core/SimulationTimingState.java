package com.lushprojects.circuitjs1.client.core;

public final class SimulationTimingState {
    public double t;                      // Current simulation time (seconds)
    public long realTimeStart;            // Real wall-clock time when simulation started (ms)
    public double timeStep;               // Current timestep (time between iterations)
    public double maxTimeStep;            // Maximum timestep (reduced when convergence is difficult)
    public double minTimeStep;            // Minimum allowed timestep
    public double timeStepAccum;          // Accumulated time since timeStepCount increment
    public int timeStepCount;             // Counter incremented each maxTimeStep advance
}
