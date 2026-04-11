package com.lushprojects.circuitjs1.client.elements;

public class ExprState {
    //int n;
    public double values[];
    public double lastValues[];
    public double lastOutput;
    double lastDiffInput;  // For diff() function - committed value from last timestep
    public double lastIntOutput;  // For integrate() function - committed value from last timestep
    private double lastIntTime;    // Last time integrate() was committed
    double pendingIntInput; // Current input value for integrate (updated each subiteration)
    double pendingDiffInput; // Current input value for diff (to be committed at stepFinished)
    boolean diffInitialized; // True after first commit, so diff has valid lastDiffInput
    public double t;
    
    // For lag() function - circular buffer of historical values
    // Each lag() call in an expression gets its own buffer, indexed by lagIndex
    private static final int LAG_BUFFER_SIZE = 10000;  // Max history entries per lag
    public static final int MAX_LAG_BUFFERS = 10;     // Max number of lag() calls per expression
    private double[][] lagBufferValues;    // [lagIndex][bufferPos] = value
    private double[][] lagBufferTimes;     // [lagIndex][bufferPos] = time
    private int[] lagBufferHead;           // Write position for each buffer
    private int[] lagBufferCount;          // Number of valid entries in each buffer
    double[] lagPendingValue;      // Current value to commit at stepFinished
    double[] lagLastCommitTime;    // Last time we committed to each buffer
    private int[] lagBufferTotalCount;     // Total entries ever added to each buffer (for debugging)
    private int lagBufferIndex;            // Current lag buffer being used during eval

	// For smooth() function - per-call implicit-Euler state
    public static final int MAX_SMOOTH_STATES = 10;  // Max number of smooth() calls per expression
	double[] smoothLastOutput;      // Last committed output for each smooth() call
	double[] smoothPendingOutput;   // Current subiteration output to commit at stepFinished
	double[] smoothLastCommitTime;  // Last time each smooth state was committed
	boolean[] smoothInitialized;    // True after first use of each smooth() call
    
    public ExprState(int xx) {
	//n = xx;
	values = new double[9];
	lastValues = new double[9];
	values[4] = Math.E;
	lastDiffInput = 0;
	lastIntOutput = 0;
	lastIntTime = -1;
	pendingIntInput = 0;
	pendingDiffInput = 0;
	diffInitialized = false;
	
	// Lightweight lag/smooth metadata arrays (10 elements each ~ 80-160 bytes).
	// The heavy circular-buffer arrays (lagBufferValues, lagBufferTimes: 10x10000
	// doubles each = ~1.6 MB) are lazy-allocated in ensureLagBuffers() on first use.
	lagBufferValues = null;
	lagBufferTimes = null;
	lagBufferHead = new int[MAX_LAG_BUFFERS];
	lagBufferCount = new int[MAX_LAG_BUFFERS];
	lagPendingValue = new double[MAX_LAG_BUFFERS];
	lagLastCommitTime = new double[MAX_LAG_BUFFERS];
	lagBufferTotalCount = new int[MAX_LAG_BUFFERS];
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    lagLastCommitTime[i] = -1;
	    lagBufferTotalCount[i] = 0;
	}
	lagBufferIndex = 0;

	// Smooth state: small arrays, always allocated.
	smoothLastOutput = new double[MAX_SMOOTH_STATES];
	smoothPendingOutput = new double[MAX_SMOOTH_STATES];
	smoothLastCommitTime = new double[MAX_SMOOTH_STATES];
	smoothInitialized = new boolean[MAX_SMOOTH_STATES];
	for (int i = 0; i < MAX_SMOOTH_STATES; i++) {
	    smoothLastCommitTime[i] = -1;
	}
    }

    /** Lazy-allocate the heavy lag circular-buffer arrays on first use. */
    private void ensureLagBuffers() {
	if (lagBufferValues == null) {
	    lagBufferValues = new double[MAX_LAG_BUFFERS][LAG_BUFFER_SIZE];
	    lagBufferTimes = new double[MAX_LAG_BUFFERS][LAG_BUFFER_SIZE];
	}
    }
    
    public void updateLastValues(double lastOut) {
	lastOutput = lastOut;
	int i;
	for (i = 0; i != values.length; i++)
	    lastValues[i] = values[i];
    }
    
    public void reset() {
	for (int i = 0; i != values.length; i++)
	    lastValues[i] = 0;
	lastOutput = 0;
	lastDiffInput = 0;
	lastIntOutput = 0;
	lastIntTime = -1;
	pendingIntInput = 0;
	pendingDiffInput = 0;
	diffInitialized = false;
	
	// Reset lag buffers (metadata only; heavy arrays freed to save memory)
	lagBufferValues = null;
	lagBufferTimes = null;
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    lagBufferHead[i] = 0;
	    lagBufferCount[i] = 0;
	    lagPendingValue[i] = 0;
	    lagLastCommitTime[i] = -1;
	    lagBufferTotalCount[i] = 0;
	}
	lagBufferIndex = 0;

	// Reset smooth() states
	for (int i = 0; i < MAX_SMOOTH_STATES; i++) {
	    smoothLastOutput[i] = 0;
	    smoothPendingOutput[i] = 0;
	    smoothLastCommitTime[i] = -1;
	    smoothInitialized[i] = false;
	}
    }
    
    // Call this at the end of each timestep to commit the integration and differentiation
    public void commitIntegration(double timeStep) {
	if (t != lastIntTime) {
	    lastIntOutput = lastIntOutput + timeStep * pendingIntInput;
	    lastIntTime = t;
	}
	lastDiffInput = pendingDiffInput;
	diffInitialized = true;
	
	// Commit lag buffer values
	for (int i = 0; i < MAX_LAG_BUFFERS; i++) {
	    if (t != lagLastCommitTime[i] && lagLastCommitTime[i] >= 0) {
		// Lazy-allocate heavy circular buffers on first actual commit
		ensureLagBuffers();
		// Add new entry to circular buffer
		lagBufferValues[i][lagBufferHead[i]] = lagPendingValue[i];
		lagBufferTimes[i][lagBufferHead[i]] = t;
		lagBufferHead[i] = (lagBufferHead[i] + 1) % LAG_BUFFER_SIZE;
		if (lagBufferCount[i] < LAG_BUFFER_SIZE) {
		    lagBufferCount[i]++;
		}
		lagBufferTotalCount[i]++;  // Track total entries ever added
		lagLastCommitTime[i] = t;
	    }
	}

	// Commit smooth() outputs (one committed state per smooth call)
	for (int i = 0; i < MAX_SMOOTH_STATES; i++) {
	    if (smoothInitialized[i] && t != smoothLastCommitTime[i]) {
		smoothLastOutput[i] = smoothPendingOutput[i];
		smoothLastCommitTime[i] = t;
	    }
	}
    }
    
    // Reset the lag buffer index at the start of each evaluation
    void resetLagIndex() {
	lagBufferIndex = 0;
    }
    
    // Get the value from the lag buffer at time (currentTime - delay)
    // Returns the input value if not enough history exists
    double getLaggedValue(int bufferIdx, double delay, double currentValue) {
	if (bufferIdx >= MAX_LAG_BUFFERS || lagBufferCount[bufferIdx] == 0
		|| lagBufferValues == null) {
	    return currentValue;  // No history yet
	}
	
	double targetTime = t - delay;
	if (targetTime < 0) {
	    return currentValue;  // Before simulation start
	}
	
	// Search backwards through the circular buffer to find the value at targetTime
	int count = lagBufferCount[bufferIdx];
	int head = lagBufferHead[bufferIdx];
	
	// Start from most recent and go backwards
	double prevTime = -1;
	double prevValue = currentValue;
	
	for (int i = 0; i < count; i++) {
	    int idx = (head - 1 - i + LAG_BUFFER_SIZE) % LAG_BUFFER_SIZE;
	    double bufTime = lagBufferTimes[bufferIdx][idx];
	    double bufValue = lagBufferValues[bufferIdx][idx];
	    
	    if (bufTime <= targetTime) {
		// Found it - interpolate if we have a next point
		if (prevTime >= 0 && prevTime > bufTime) {
		    // Linear interpolation between bufTime and prevTime
		    double alpha = (targetTime - bufTime) / (prevTime - bufTime);
		    return bufValue + alpha * (prevValue - bufValue);
		}
		return bufValue;
	    }
	    prevTime = bufTime;
	    prevValue = bufValue;
	}
	
	// targetTime is before our oldest record - return oldest value
	int oldestIdx = (head - count + LAG_BUFFER_SIZE) % LAG_BUFFER_SIZE;
	return lagBufferValues[bufferIdx][oldestIdx];
    }
    
    // Get a simple moving average of lag buffer values over one period
    // Averages all samples within the time window [currentTime - delay, currentTime]
    // For timestep=0.01 and delay=1, this averages ~100 points
    double getLaggedMovingAverage(int bufferIdx, double delay, double initValue) {
	if (bufferIdx >= MAX_LAG_BUFFERS || lagBufferCount[bufferIdx] == 0
		|| lagBufferValues == null) {
	    return initValue;  // No history yet
	}
	
	int count = lagBufferCount[bufferIdx];
	int head = lagBufferHead[bufferIdx];
	double windowStart = t - delay;
	
	// Simple moving average: sum values within the window
	double sum = lagPendingValue[bufferIdx];  // Include current pending value
	int numPoints = 1;
	
	for (int i = 0; i < count; i++) {
	    int idx = (head - 1 - i + LAG_BUFFER_SIZE) % LAG_BUFFER_SIZE;
	    double bufTime = lagBufferTimes[bufferIdx][idx];
	    
	    // Only include entries within our window
	    if (bufTime < windowStart) {
		break;  // Older entries are outside the window
	    }
	    
	    sum += lagBufferValues[bufferIdx][idx];
	    numPoints++;
	}
	
	// If window extends before buffer history, pad with initValue
	// This smoothly transitions from initValue to actual data
	if (windowStart < 0 && numPoints > 0) {
	    // Calculate how many "virtual" init samples to add
	    // based on how much of the window is before t=0
	    double fractionBeforeStart = -windowStart / delay;
	    int initPoints = (int)(fractionBeforeStart * numPoints);
	    if (initPoints > 0) {
		sum += initValue * initPoints;
		numPoints += initPoints;
	    }
	}
	
	return sum / numPoints;
    }
}
