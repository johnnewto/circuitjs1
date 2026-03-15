package com.lushprojects.circuitjs1.client;

import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class HeadlessRunner {

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: HeadlessRunner <circuit.txt> [output.csv] [steps=1000]");
            System.exit(1);
        }

        RuntimeMode.setHeadless(true);
        ComputedValues.resetForTesting();

        String circuitPath = args[0];
        String outputPath = args.length > 1 && args[1] != null && !args[1].trim().isEmpty() ? args[1] : null;
        int steps = args.length > 2 ? Integer.parseInt(args[2]) : 1000;

        Path circuitFilePath = Paths.get(circuitPath);
        String circuitText = new String(Files.readAllBytes(circuitFilePath), StandardCharsets.UTF_8);

        CirSim sim = new CirSim();
        sim.initHeadless();
        sim.readCircuit(circuitText, 0);
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();

        if (sim.elmList == null || sim.elmList.size() == 0) {
            System.err.println("HeadlessRunner: no circuit elements available after load");
        }
        if (sim.circuitMatrix == null) {
            System.err.println("HeadlessRunner: circuit matrix is null after analyze (no solvable electrical network)");
        }

        if (sim.stopMessage != null) {
            System.err.println("HeadlessRunner: simulation stopped during analyze: " + sim.stopMessage);
        }

        PrintWriter out = outputPath != null
                ? new PrintWriter(new FileWriter(outputPath))
                : new PrintWriter(new OutputStreamWriter(System.out));

        Set<String> registered = ComputedValues.getRegisteredComputedNames();
        List<String> keys = new ArrayList<String>(registered != null ? registered : Collections.<String>emptySet());
        Collections.sort(keys);

        out.print("t");
        for (String key : keys) {
            out.print("," + key);
        }
        out.println();

        boolean warnedNoTimeAdvance = false;
        for (int step = 0; step < steps; step++) {
            double prevT = sim.t;
            sim.runCircuit(step == 0);
            ComputedValues.commitConvergedValues();

            if (sim.stopMessage != null) {
                System.err.println("HeadlessRunner: simulation stopped at step " + step + ": " + sim.stopMessage);
            }
            if (!warnedNoTimeAdvance && sim.t == prevT) {
                warnedNoTimeAdvance = true;
                System.err.println("HeadlessRunner: time did not advance at step " + step + " (t=" + sim.t + ")");
            }

            out.print(sim.t);
            for (String key : keys) {
                Double value = ComputedValues.getConvergedValue(key);
                out.print("," + (value != null ? value : ""));
            }
            out.println();

            if (sim.stopMessage != null) {
                break;
            }
        }

        out.flush();
        if (outputPath != null) {
            out.close();
        }
    }
}
