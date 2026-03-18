package johnnewto.world2.server;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import johnnewto.world2.World2DataPoint;
import johnnewto.world2.World2RunResult;
import johnnewto.world2.World2Scenario;
import johnnewto.world2.World2ScenarioLibrary;
import johnnewto.world2.World2Simulator;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.Executors;

public class World2Server {
    private static final int DEFAULT_PORT = 18082;

    private final World2ScenarioLibrary scenarioLibrary;
    private final World2Simulator simulator;

    public World2Server(World2ScenarioLibrary scenarioLibrary, World2Simulator simulator) {
        this.scenarioLibrary = scenarioLibrary;
        this.simulator = simulator;
    }

    public static void main(String[] args) throws Exception {
        int port = DEFAULT_PORT;
        if (args != null && args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {
                port = DEFAULT_PORT;
            }
        }

        World2ScenarioLibrary library = new World2ScenarioLibrary();
        World2Simulator simulator = new World2Simulator(library);
        World2Server app = new World2Server(library, simulator);
        app.start(port);
    }

    public void start(int port) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", port), 0);
        server.createContext("/health", new HealthHandler());
        server.createContext("/scenarios", new ScenariosHandler());
        server.createContext("/run", new RunJsonHandler());
        server.createContext("/run.csv", new RunCsvHandler());
        server.setExecutor(Executors.newFixedThreadPool(4));
        server.start();
        System.out.println("World2 server started on http://127.0.0.1:" + port);
    }

    private final class HealthHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            sendJson(exchange, 200, "{\"status\":\"ok\"}");
        }
    }

    private final class ScenariosHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            List<World2Scenario> scenarios = scenarioLibrary.getAllScenarios();
            StringBuilder json = new StringBuilder();
            json.append("{\"scenarios\":[");
            for (int index = 0; index < scenarios.size(); index++) {
                World2Scenario scenario = scenarios.get(index);
                if (index > 0) {
                    json.append(',');
                }
                json.append("{\"id\":\"").append(escapeJson(scenario.getId())).append("\",")
                    .append("\"name\":\"").append(escapeJson(scenario.getDisplayName())).append("\"}");
            }
            json.append("]}");
            sendJson(exchange, 200, json.toString());
        }
    }

    private final class RunJsonHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            try {
                RequestParams params = parseRequestParams(exchange.getRequestURI());
                World2RunResult result = simulator.run(params.scenarioId, params.steps, params.dt);
                String json = runResultToJson(result);
                sendJson(exchange, 200, json);
            } catch (IllegalArgumentException exception) {
                sendJson(exchange, 400, "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
            } catch (Exception exception) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
            }
        }
    }

    private final class RunCsvHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendMethodNotAllowed(exchange);
                return;
            }
            try {
                RequestParams params = parseRequestParams(exchange.getRequestURI());
                World2RunResult result = simulator.run(params.scenarioId, params.steps, params.dt);
                sendText(exchange, 200, "text/csv; charset=utf-8", result.toCsv());
            } catch (IllegalArgumentException exception) {
                sendJson(exchange, 400, "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
            } catch (Exception exception) {
                sendJson(exchange, 500, "{\"error\":\"" + escapeJson(exception.getMessage()) + "\"}");
            }
        }
    }

    private static class RequestParams {
        private final String scenarioId;
        private final int steps;
        private final double dt;

        private RequestParams(String scenarioId, int steps, double dt) {
            this.scenarioId = scenarioId;
            this.steps = steps;
            this.dt = dt;
        }
    }

    private RequestParams parseRequestParams(URI uri) {
        Map<String, String> query = parseQuery(uri.getRawQuery());
        String scenarioId = valueOrDefault(query.get("scenario"), "1");
        int steps = parsePositiveInt(query.get("steps"), 1000);
        double dt = parsePositiveDouble(query.get("dt"), 0.2);
        return new RequestParams(scenarioId, steps, dt);
    }

    private static String valueOrDefault(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim();
    }

    private static int parsePositiveInt(String value, int defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static double parsePositiveDouble(String value, double defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        try {
            double parsed = Double.parseDouble(value.trim());
            return parsed > 0 ? parsed : defaultValue;
        } catch (Exception ignored) {
            return defaultValue;
        }
    }

    private static Map<String, String> parseQuery(String rawQuery) {
        Map<String, String> query = new LinkedHashMap<String, String>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return query;
        }
        String[] pairs = rawQuery.split("&");
        for (int index = 0; index < pairs.length; index++) {
            String pair = pairs[index];
            if (pair.isEmpty()) {
                continue;
            }
            String[] keyValue = pair.split("=", 2);
            String key = decodeUrl(keyValue[0]);
            String value = keyValue.length > 1 ? decodeUrl(keyValue[1]) : "";
            query.put(key, value);
        }
        return query;
    }

    private static String decodeUrl(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void sendMethodNotAllowed(HttpExchange exchange) throws IOException {
        sendText(exchange, 405, "text/plain; charset=utf-8", "Method not allowed");
    }

    private static void sendJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        sendText(exchange, statusCode, "application/json; charset=utf-8", body);
    }

    private static void sendText(HttpExchange exchange, int statusCode, String contentType, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        try (OutputStream out = exchange.getResponseBody()) {
            out.write(bytes);
        }
    }

    private String runResultToJson(World2RunResult result) {
        StringBuilder json = new StringBuilder();
        json.append("{")
            .append("\"scenario\":\"").append(escapeJson(result.getScenario().getId())).append("\",")
            .append("\"name\":\"").append(escapeJson(result.getScenario().getDisplayName())).append("\",")
            .append("\"dt\":").append(formatDouble(result.getDt())).append(",")
            .append("\"series\":[");

        List<World2DataPoint> series = result.getSeries();
        for (int index = 0; index < series.size(); index++) {
            World2DataPoint point = series.get(index);
            if (index > 0) {
                json.append(',');
            }
            json.append("{")
                .append("\"t\":").append(formatDouble(point.getTime())).append(",")
                .append("\"P\":").append(formatDouble(point.getPopulation())).append(",")
                .append("\"POLR\":").append(formatDouble(point.getPollutionRatio())).append(",")
                .append("\"CI\":").append(formatDouble(point.getCapitalInvestment())).append(",")
                .append("\"QL\":").append(formatDouble(point.getQualityOfLife())).append(",")
                .append("\"NR\":").append(formatDouble(point.getNaturalResources()))
                .append("}");
        }

        json.append("]}");
        return json.toString();
    }

    private static String formatDouble(double value) {
        return String.format(Locale.US, "%.8f", value);
    }

    private static String escapeJson(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
