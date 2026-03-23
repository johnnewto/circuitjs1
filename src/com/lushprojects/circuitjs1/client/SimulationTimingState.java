package com.lushprojects.circuitjs1.client;

final class SimulationTimingState {
    double t;                      // Current simulation time (seconds)
    long realTimeStart;            // Real wall-clock time when simulation started (ms)
    double timeStep;               // Current timestep (time between iterations)
    double maxTimeStep;            // Maximum timestep (reduced when convergence is difficult)
    double minTimeStep;            // Minimum allowed timestep
    double timeStepAccum;          // Accumulated time since timeStepCount increment
    int timeStepCount;             // Counter incremented each maxTimeStep advance
}
