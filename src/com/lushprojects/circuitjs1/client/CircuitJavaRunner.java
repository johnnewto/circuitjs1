package com.lushprojects.circuitjs1.client;

import java.io.FileWriter;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Locale;

/**
 * Command-line interface for running CircuitJS1 simulations on the JVM / terminal, i.e. no GWT, not in the browser.
 * 
 * <p>This class enables non-interactive simulation execution for automated testing, batch processing,
 * and data export. It bypasses the GWT/browser UI and runs the circuit simulation engine
 * directly on the JVM.
 * 
 * <h2>Usage</h2>
 * <pre>
 * ./gradlew runCircuitJava -Pcircuit="path/to/circuit.txt" -Psteps=1000 -Pformat=csv -Phtml="/tmp/world2.html"
 * </pre>
 * 
 * <h2>Arguments</h2>
 * <ul>
 *   <li><b>circuit</b> (required): Path to the circuit file (.txt or .md with embedded circuit)</li>
 *   <li><b>output</b> (optional): Output file path. If omitted, writes to stdout</li>
 *   <li><b>steps</b> (optional): Number of simulation steps to run (default: 1000)</li>
 *   <li><b>format</b> (optional): Output format - "csv" or "world2" (default: csv)</li>
 *   <li><b>html</b> (optional): Output path for an HTML report. World2 format generates table + plots</li>
 * </ul>
 * 
 * <h2>Output Formats</h2>
 * <ul>
 *   <li><b>csv</b>: Comma-separated values with all registered ComputedValues variables</li>
 *   <li><b>world2</b>: Tab-separated format with specific World2 economic model variables
 *       (P, POLR, CI, QL, NR/NL) using SI prefixes for large numbers</li>
 * </ul>
 * 
 * <h2>Design Notes</h2>
 * <ul>
 *   <li>This file is <b>excluded from GWT compilation</b> (see circuitjs1.gwt.xml)</li>
 *   <li>Uses JVM-only APIs: {@code String.format()}, {@code java.nio.file}, etc.</li>
 *   <li>Sets {@code RuntimeMode.setNonInteractiveRuntime(true)} so elements skip drawing code</li>
 *   <li>Outputs values from {@link ComputedValues} registry (populated by table elements)</li>
 * </ul>
 * 
 * @see ComputedValues
 * @see RuntimeMode#setNonInteractiveRuntime(boolean)
 */
public class CircuitJavaRunner {

    /**
     * Main entry point for non-interactive circuit simulation.
     * 
     * <p>Loads a circuit file, runs the simulation for the specified number of steps,
     * and outputs computed values in the requested format.
     * 
     * @param args command-line arguments: circuit_path [output_path] [steps] [format]
     * @throws Exception if circuit file cannot be read or simulation fails
     */
    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
                System.err.println("Usage: CircuitJavaRunner <circuit.txt> [output.csv] [steps=1000] [format=csv|world2] [output.html]");
            System.exit(1);
        }

        RuntimeMode.setNonInteractiveRuntime(true);
        ComputedValues.resetForTesting();

        String circuitPath = args[0];
        String outputPath = args.length > 1 && args[1] != null && !args[1].trim().isEmpty() ? args[1] : null;
        int steps = args.length > 2 ? Integer.parseInt(args[2]) : 1000;
        String format = args.length > 3 && args[3] != null && !args[3].trim().isEmpty()
            ? args[3].trim().toLowerCase(Locale.ROOT)
            : "csv";
        String htmlPath = args.length > 4 && args[4] != null && !args[4].trim().isEmpty() ? args[4] : null;

        Path circuitFilePath = Paths.get(circuitPath);
        String circuitText = new String(Files.readAllBytes(circuitFilePath), StandardCharsets.UTF_8);

        CirSim sim = new CirSim();
    sim.getBootstrap().initRunner();
        sim.getCircuitIOService().readCircuit(circuitText, 0);
        sim.analyzeCircuit();
        sim.preStampAndStampCircuit();

        if (sim.elmList == null || sim.elmList.size() == 0) {
            System.err.println("CircuitJavaRunner: no circuit elements available after load");
        }
        if (sim.circuitMatrix == null) {
            System.err.println("CircuitJavaRunner: circuit matrix is null after analyze (no solvable electrical network)");
        }

        if (sim.stopMessage != null) {
            System.err.println("CircuitJavaRunner: simulation stopped during analyze: " + sim.stopMessage);
        }

        SimulationExportCore.RunRequest runRequest = new SimulationExportCore.RunRequest();
        runRequest.circuitPath = circuitPath;
        runRequest.outputPath = outputPath;
        runRequest.htmlPath = htmlPath;
        runRequest.steps = steps;
        runRequest.format = format;
        SimulationExportCore.RunResult runResult = SimulationExportCore.run(sim, runRequest, System.err::println);

        PrintWriter out = outputPath != null
                ? new PrintWriter(new FileWriter(outputPath))
                : new PrintWriter(new OutputStreamWriter(System.out));
        out.print(runResult.outputText);
        out.flush();
        if (outputPath != null) {
            out.close();
        }

        if (htmlPath != null) {
            if (!runResult.world2Format) {
                System.err.println("CircuitJavaRunner: HTML report generation currently supports format=world2 only");
            } else {
                Files.write(Paths.get(htmlPath), runResult.htmlReport.getBytes(StandardCharsets.UTF_8));
                System.err.println("CircuitJavaRunner: wrote HTML report to " + htmlPath);
            }
        }
    }
}
