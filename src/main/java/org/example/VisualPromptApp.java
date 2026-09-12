package org.example;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisualPromptApp {


    private static final int SERVER_PORT = 8080;
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern INLINE_AR_PATTERN = Pattern.compile("--ar\\s+([0-9]+:[0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final String[] SPINNER_CHARS = {"\u280B", "\u2819", "\u2839", "\u2838", "\u283C", "\u2834", "\u2826", "\u2827", "\u2807", "\u280F"};
    private static final int MAX_OUTPUT_TOKENS = 4000;
    private static final String RULE = "---------------------------------------------------------------------------";

    public enum GeminiModel {
        FLASH("gemini-3.6-flash", "Gemini 3.6 Flash (Fast Draft)"),
        PRO("gemini-3.1-pro", "Gemini 3.1 Pro (High-Fidelity Reasoning)");

        private final String endpointId;
        private final String label;

        GeminiModel(String endpointId, String label) {
            this.endpointId = endpointId;
            this.label = label;
        }

        public String getEndpointId() {
            return endpointId;
        }

        public String getLabel() {
            return label;
        }
    }

    public enum IngestionMode {
        DIRECT, NARRATIVE, THREE_STAGE
    }

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final NarrativeExtractor narrativeExtractor;
    private final ThreeStageIngestor threeStageIngestor;

    private PromptCompiler.EngineProfile activeEngine = PromptCompiler.EngineProfile.MIDJOURNEY_V6;
    private GeminiModel activeModel = GeminiModel.FLASH;
    private IngestionMode currentMode = IngestionMode.DIRECT;

    private String currentAspectRatio = "16:9";
    private String customMjFlags = "--style raw --v 6.1";
    private PromptCompiler.CompiledOutput lastOutput = null;

    /** Gates generation, not just display: when off, regionalPasses leaves the schema entirely. */
    private boolean regionalPassesEnabled = true;

    public VisualPromptApp(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new IllegalStateException(
                    "GEMINI_API_KEY is not set. Export a valid Gemini API key before starting the compiler:\n"
                            + "  export GEMINI_API_KEY=your-real-key-here");
        }
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper()
                .configure(DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES, true)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        this.apiKey = apiKey.trim();
        this.narrativeExtractor = new NarrativeExtractor();
        this.threeStageIngestor = new ThreeStageIngestor();
    }

    public static void main(String[] args) {
        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey == null || envKey.isBlank()) {
            envKey = "buraya gemini apı anahtarı yapıştır";
        }
        VisualPromptApp app;
        try {
            app = new VisualPromptApp(envKey);
        } catch (IllegalStateException e) {
            System.err.println("[FATAL] " + e.getMessage());
            System.exit(1);
            return;
        }

        // Background Web Server Daemon
        app.startWebServer();

        // Terminal Interactive Loop
        app.start();
    }

    // ---------------------------------------------------------------------
    // Web Server Layer (Bridges HTTP UI without touching CLI engine)
    // ---------------------------------------------------------------------

    public void startWebServer() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(SERVER_PORT), 0);
            server.createContext("/", this::handleStaticFile);
            server.createContext("/api/compile", this::handleApiCompile);
            server.setExecutor(Executors.newFixedThreadPool(4, r -> {
                Thread t = new Thread(r, "web-compiler-worker");
                t.setDaemon(true);
                return t;
            }));
            server.start();
            System.out.println("[\u2713] Web UI Daemon active at: http://localhost:" + SERVER_PORT);
        } catch (IOException e) {
            System.err.println("[!] Web UI Server could not start on port " + SERVER_PORT + ": " + e.getMessage());
        }
    }

    private void handleStaticFile(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        String pathStr = exchange.getRequestURI().getPath();

        // Aday dosya yollarını tara: Kök dizin, resources dizini ve web kökü
        List<Path> candidates = new ArrayList<>();
        if (pathStr.equals("/") || pathStr.equals("/anasayfa.html") || pathStr.equals("/index.html")) {
            candidates.add(Path.of("anasayfa.html"));
            candidates.add(Path.of("index.html"));
            candidates.add(Path.of("src/main/resources/anasayfa.html"));
            candidates.add(Path.of("src/main/resources/index.html"));
        } else {
            String clean = pathStr.startsWith("/") ? pathStr.substring(1) : pathStr;
            candidates.add(Path.of(clean));
            candidates.add(Path.of("src/main/resources", clean));
        }

        Path target = null;
        for (Path p : candidates) {
            if (Files.exists(p) && !Files.isDirectory(p)) {
                target = p;
                break;
            }
        }

        if (target == null) {
            String cwd = Path.of(".").toAbsolutePath().normalize().toString();
            String err = "404 Not Found - Dosya bulunamadi.\nCalisma Dizini (CWD): " + cwd
                    + "\nAranan Adaylar: " + candidates.toString();
            byte[] notFound = err.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(404, notFound.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(notFound);
            }
            return;
        }

        String contentType = target.toString().endsWith(".js") ? "text/javascript"
                : target.toString().endsWith(".css") ? "text/css"
                : "text/html; charset=UTF-8";

        byte[] fileBytes = Files.readAllBytes(target);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(200, fileBytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(fileBytes);
        }
    }

    private void handleApiCompile(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.getResponseHeaders().set("Access-Control-Allow-Methods", "POST, OPTIONS");
        exchange.getResponseHeaders().set("Access-Control-Allow-Headers", "Content-Type");

        if ("OPTIONS".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(204, -1);
            return;
        }

        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.sendResponseHeaders(405, -1);
            return;
        }

        try (InputStream is = exchange.getRequestBody()) {
            JsonNode req = objectMapper.readTree(is);

            String modeStr = req.path("mode").asText("THREE_STAGE").toUpperCase();
            IngestionMode mode;
            try {
                mode = IngestionMode.valueOf(modeStr);
            } catch (IllegalArgumentException ex) {
                mode = IngestionMode.THREE_STAGE;
            }

            String engineStr = req.path("engine").asText("mj");
            PromptCompiler.EngineProfile targetEngine = "flux".equalsIgnoreCase(engineStr)
                    ? PromptCompiler.EngineProfile.FLUX_1_DEV
                    : PromptCompiler.EngineProfile.MIDJOURNEY_V6;

            String modelStr = req.path("model").asText("flash");
            GeminiModel targetModel = "pro".equalsIgnoreCase(modelStr) ? GeminiModel.PRO : GeminiModel.FLASH;

            String requestedAr = req.path("ar").asText("16:9");
            String customFlags = req.path("flags").asText("--style raw --v 6.1");
            boolean regionalPasses = req.path("regionalPasses").asBoolean(true);

            List<String> reportLogs = new ArrayList<>();
            String sceneText;
            String effectiveAr = requestedAr;
            String effectiveFlags = customFlags;

            if (mode == IngestionMode.THREE_STAGE) {
                String slotOne = req.path("slotOne").asText("");
                String slotTwo = req.path("slotTwo").asText("");
                String slotThree = req.path("slotThree").asText("");

                ThreeStageIngestor.StagedScene staged = threeStageIngestor.stage(
                        slotOne, slotTwo, slotThree, targetModel.getEndpointId(), apiKey);

                reportLogs.addAll(staged.notes());
                ThreeStageIngestor.Directives directives = staged.directives();

                if (directives.hasAspectRatio()) {
                    effectiveAr = directives.aspectRatio();
                    reportLogs.add("Slot 3 aspect ratio override: --ar " + effectiveAr);
                }
                if (directives.hasEngineFlags()) {
                    effectiveFlags = directives.engineFlagString();
                    reportLogs.add("Slot 3 engine flags applied: " + effectiveFlags);
                }

                sceneText = staged.pipelineText();
            } else {
                String rawInput = req.path("rawScene").asText("");
                sceneText = resolveSceneContent(rawInput);

                if (mode == IngestionMode.NARRATIVE) {
                    reportLogs.add("Resolving physical keyframe from narrative sequence...");
                    sceneText = narrativeExtractor.extractKeyframe(sceneText, targetModel.getEndpointId(), apiKey);
                    reportLogs.add("Keyframe isolated: " + sceneText);
                }
            }

            Matcher arMatcher = INLINE_AR_PATTERN.matcher(sceneText);
            if (arMatcher.find()) {
                effectiveAr = arMatcher.group(1);
                sceneText = arMatcher.replaceAll("").trim();
                reportLogs.add("Inline aspect ratio override: --ar " + effectiveAr);
            }

            // Execute full deterministic backbone with existing settings
            boolean prevRegionalState = this.regionalPassesEnabled;
            this.regionalPassesEnabled = regionalPasses;
            SceneContract contract;
            try {
                contract = streamStructuredContract(sceneText);
            } finally {
                this.regionalPassesEnabled = prevRegionalState;
            }

            String structuralReport = PromptCompiler.generateStructuralReport(contract);
            String dynamicMjFlags = "--ar " + effectiveAr + " " + effectiveFlags;
            PromptCompiler.CompiledOutput output = PromptCompiler.compile(contract, targetEngine, dynamicMjFlags);

            // Native Desktop Clipboard sync
            copyToClipboard(output.clipboardText());

            ObjectNode res = objectMapper.createObjectNode();
            res.put("positivePrompt", output.positivePrompt());
            res.put("negativePrompt", output.negativePrompt());
            res.put("flags", output.flags());
            res.put("clipboardText", output.clipboardText());
            res.put("structuralReport", structuralReport);
            res.put("hasRegionalPrompts", output.hasRegionalPrompts());

            if (output.hasRegionalPrompts()) {
                res.put("regionalManifest", PromptCompiler.compileRegionalManifest(output));
                ArrayNode rArr = res.putArray("regionalPrompts");
                for (PromptCompiler.RegionalPrompt pass : output.regionalPrompts()) {
                    ObjectNode pNode = rArr.addObject();
                    pNode.put("zone", pass.zone());
                    pNode.put("bounding", pass.boundingDescription());
                    pNode.put("prompt", pass.prompt());
                }
            }

            ArrayNode logArray = res.putArray("logs");
            reportLogs.forEach(logArray::add);

            byte[] respBytes = objectMapper.writeValueAsBytes(res);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, respBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(respBytes);
            }

        } catch (Exception e) {
            ObjectNode errNode = objectMapper.createObjectNode();
            errNode.put("error", e.getMessage());
            byte[] errBytes = objectMapper.writeValueAsBytes(errNode);
            exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(500, errBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(errBytes);
            }
        }
    }

    // ---------------------------------------------------------------------
    // CLI Interactive Loop (Unchanged Original)
    // ---------------------------------------------------------------------

    public void start() {
        printBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            printStatusLine();

            try {
                if (currentMode == IngestionMode.THREE_STAGE) {
                    if (runThreeStageTurn(reader)) {
                        break;
                    }
                    continue;
                }

                System.out.println("> Enter scene description, .txt file path, or command (:help, :exit):");
                System.out.println("  (Type 'END' on a single line or press Enter twice to compile)");

                String input = readBlockInput(reader);
                if (input == null) {
                    System.out.println("\nExiting Visual Prompt Compiler.");
                    break;
                }

                String trimmed = input.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                if (trimmed.startsWith(":")) {
                    if (handleCommand(trimmed, reader)) {
                        break;
                    }
                    continue;
                }

                String sceneText = resolveSceneContent(trimmed);

                if (currentMode == IngestionMode.NARRATIVE) {
                    System.out.println("[...] Resolving physical keyframe from narrative sequence...");
                    sceneText = narrativeExtractor.extractKeyframe(sceneText, activeModel.getEndpointId(), apiKey);
                    System.out.println("[\u2713] Keyframe isolated:\n" + sceneText);
                }

                compileAndEmit(sceneText, null, null);

            } catch (IOException e) {
                System.err.println("\n[I/O Error] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("\n[Thread Interrupted] " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("\n[Pipeline Error] " + e.getMessage());
            }
        }
    }

    // ---------------------------------------------------------------------
    // Three-stage ingestion turn
    // ---------------------------------------------------------------------

    /**
     * Collects the three payload-contract slots, stages them through
     * {@link ThreeStageIngestor}, and feeds the staged prose into the unchanged
     * SceneContract -> PromptCompiler backbone.
     *
     * @return true when the session should terminate.
     */
    private boolean runThreeStageTurn(BufferedReader reader) throws Exception {
        System.out.println("> [SLOT 1/3 \u2014 ENVIRONMENT_SPATIAL]  location / era / particulates / geometry / light");
        System.out.println("  (one 'key: value' per line, or free prose. 'END' or two blank lines to advance.)");

        String slotOne = readBlockInput(reader);
        if (slotOne == null) {
            System.out.println("\nExiting Visual Prompt Compiler.");
            return true;
        }
        String slotOneTrimmed = slotOne.trim();
        if (slotOneTrimmed.startsWith(":")) {
            return handleCommand(slotOneTrimmed, reader);
        }
        if (slotOneTrimmed.isEmpty()) {
            return false;
        }

        System.out.println();
        System.out.println("> [SLOT 2/3 \u2014 DRAMATIC_ACTION]  subject_count / trajectory / contact_points / compression / kinematic_chains");
        String slotTwo = readBlockInput(reader);
        if (slotTwo == null) {
            System.out.println("\nExiting Visual Prompt Compiler.");
            return true;
        }
        if (slotTwo.trim().startsWith(":")) {
            System.out.println("[!] Staging aborted at slot 2.");
            return handleCommand(slotTwo.trim(), reader);
        }
        if (slotTwo.trim().isEmpty()) {
            System.out.println("[!] Slot 2 is mandatory \u2014 staging aborted.");
            return false;
        }

        System.out.println();
        System.out.println("> [SLOT 3/3 \u2014 OPTIONAL_DIRECTIVES]  lens / elevation / aspect_ratio / engine_flags");
        System.out.println("  (type 'END' on its own line to skip.)");
        String slotThree = readBlockInput(reader);
        if (slotThree == null) {
            System.out.println("\nExiting Visual Prompt Compiler.");
            return true;
        }
        if (slotThree.trim().startsWith(":")) {
            System.out.println("[!] Staging aborted at slot 3.");
            return handleCommand(slotThree.trim(), reader);
        }

        System.out.println();
        System.out.println("[...] Executing 3-stage spatial and kinetic allocation...");
        ThreeStageIngestor.StagedScene staged = threeStageIngestor.stage(
                slotOne, slotTwo, slotThree, activeModel.getEndpointId(), apiKey);

        for (String note : staged.notes()) {
            System.out.println("[i] " + note);
        }

        System.out.println("[\u2713] Slot allocation complete.");
        System.out.println(RULE);
        System.out.println("[SLOT VIEW / MIDJOURNEY_V6] " + staged.midjourneyView());
        System.out.println("[SLOT VIEW / FLUX_1_DEV]    " + staged.fluxProse());
        System.out.println(RULE);
        System.out.println("[i] Staged prose now enters the SceneContract backbone for deterministic compilation.");
        System.out.println();

        ThreeStageIngestor.Directives directives = staged.directives();
        if (directives.hasAspectRatio()) {
            System.out.println("[\u2713] Slot 3 aspect ratio override: --ar " + directives.aspectRatio());
        }
        if (directives.hasEngineFlags()) {
            // Replacement rather than append: mergeFlags concatenates duplicate keys into a
            // single malformed value, so a slot-3 flag set supersedes :flags for this run.
            System.out.println("[\u2713] Slot 3 engine flags supersede :flags for this run: " + directives.engineFlagString());
        }

        compileAndEmit(
                staged.pipelineText(),
                directives.hasAspectRatio() ? directives.aspectRatio() : null,
                directives.hasEngineFlags() ? directives.engineFlagString() : null);

        return false;
    }

    // ---------------------------------------------------------------------
    // Shared compile path (all three ingestion modes converge here)
    // ---------------------------------------------------------------------

    private void compileAndEmit(String rawScene, String aspectRatioOverride, String flagsOverride) throws Exception {
        String sceneText = rawScene;

        String effectiveAr = (aspectRatioOverride == null || aspectRatioOverride.isBlank())
                ? currentAspectRatio
                : aspectRatioOverride;

        Matcher arMatcher = INLINE_AR_PATTERN.matcher(sceneText);
        if (arMatcher.find()) {
            effectiveAr = arMatcher.group(1);
            sceneText = arMatcher.replaceAll("").trim();
            System.out.println("[\u2713] Inline aspect ratio override: --ar " + effectiveAr);
        }

        SceneContract contract = streamStructuredContract(sceneText);

        System.out.println(PromptCompiler.generateStructuralReport(contract));

        String flagBody = (flagsOverride == null || flagsOverride.isBlank()) ? customMjFlags : flagsOverride;
        String dynamicMjFlags = "--ar " + effectiveAr + " " + flagBody;

        PromptCompiler.CompiledOutput output = PromptCompiler.compile(contract, activeEngine, dynamicMjFlags);
        this.lastOutput = output;

        System.out.println();
        System.out.println("[PHASE 3: COMPILED DIFFUSION PROMPT]");
        System.out.println(RULE);
        System.out.println(output.clipboardText());
        System.out.println(RULE);

        if (activeEngine == PromptCompiler.EngineProfile.FLUX_1_DEV
                && output.negativePrompt() != null && !output.negativePrompt().isBlank()) {
            System.out.println("[NEGATIVE PROMPT] " + output.negativePrompt());
            System.out.println(RULE);
        }

        if (copyToClipboard(output.clipboardText())) {
            System.out.println("[\u2713] Compiled prompt copied to system clipboard.");
        } else {
            System.out.println("[!] System clipboard unavailable (headless environment).");
        }

        if (output.hasRegionalPrompts()) {
            System.out.println();
            System.out.println(PromptCompiler.compileRegionalManifest(output));
            System.out.println("[i] :regional step to walk the passes, or :copy N to copy pass N.");
        }
    }

    // ---------------------------------------------------------------------
    // Structured extraction
    // ---------------------------------------------------------------------

    private SceneContract streamStructuredContract(String rawScene) throws Exception {
        // Key travels as a header, not a query parameter, so it cannot land in proxy logs
        // or be echoed back inside an error body.
        String endpointUrl = API_BASE_URL + activeModel.getEndpointId() + ":streamGenerateContent?alt=sse";

        ObjectNode rootNode = objectMapper.createObjectNode();
        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text",
                "Extract grounded physical and visual parameters conforming to the response schema. "
                        + "Capture explicit subject archetypes, visible attire/armor, active energy/powers, "
                        + "and kinetic vectors. Do not strip character visual traits or fantasy effects. "
                        + "Omit optional fields entirely when absent rather than filling them with placeholders.");

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", "Raw scene draft to structure:\n" + rawScene);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.set("response_schema",
                SceneContract.buildGeminiResponseSchema(objectMapper, regionalPassesEnabled));
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", MAX_OUTPUT_TOKENS);

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .header("x-goog-api-key", apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(120))
                .build();

        CompletableFuture<HttpResponse<Stream<String>>> futureResponse =
                httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines());

        ScheduledExecutorService progressExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "scene-contract-progress");
            t.setDaemon(true);
            return t;
        });

        long start = System.currentTimeMillis();
        AtomicInteger tick = new AtomicInteger(0);
        progressExecutor.scheduleAtFixedRate(() -> {
            double elapsed = (System.currentTimeMillis() - start) / 1000.0;
            int idx = tick.getAndIncrement() % SPINNER_CHARS.length;
            System.out.printf("\r[Compiling SceneContract %s %.1fs elapsed] ", SPINNER_CHARS[idx], elapsed);
            System.out.flush();
        }, 0, 80, TimeUnit.MILLISECONDS);

        HttpResponse<Stream<String>> response;
        try {
            response = futureResponse.get();
        } finally {
            progressExecutor.shutdownNow();
            progressExecutor.awaitTermination(1, TimeUnit.SECONDS);
            System.out.print("\r" + " ".repeat(60) + "\r");
        }

        if (response.statusCode() != 200) {
            String errorBody;
            try (Stream<String> errorLines = response.body()) {
                errorBody = errorLines.collect(Collectors.joining("\n"));
            }
            throw new IOException("Gemini API rejected request (HTTP " + response.statusCode() + "): " + errorBody);
        }

        StringBuilder jsonAccumulator = new StringBuilder();
        int chunkCount = 0;
        boolean sawAnyDataLine = false;
        String finishReason = "";

        try (Stream<String> lines = response.body()) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line;
                try {
                    line = iterator.next();
                } catch (RuntimeException streamFault) {
                    throw new IOException(
                            "SSE stream interrupted after " + chunkCount + " chunk(s); accumulated "
                                    + jsonAccumulator.length() + " chars before failure.",
                            streamFault);
                }

                if (!line.startsWith("data:")) {
                    continue;
                }
                String data = line.substring(5).trim();
                if (data.isEmpty() || data.equals("[DONE]")) {
                    continue;
                }

                sawAnyDataLine = true;

                JsonNode root;
                try {
                    root = objectMapper.readTree(data);
                } catch (IOException malformed) {
                    throw new IOException("Malformed SSE JSON chunk #" + (chunkCount + 1) + ": "
                            + malformed.getMessage(), malformed);
                }

                if (root.has("error")) {
                    throw new IOException("API Error: " + root.path("error").path("message").asText());
                }

                JsonNode candidates = root.path("candidates");
                if (candidates.isArray() && !candidates.isEmpty()) {
                    JsonNode candidate = candidates.get(0);

                    String reason = candidate.path("finishReason").asText("");
                    if (reason != null && !reason.isBlank()) {
                        finishReason = reason;
                    }

                    JsonNode parts = candidate.path("content").path("parts");
                    if (parts.isArray() && !parts.isEmpty()) {
                        for (JsonNode part : parts) {
                            if (part.has("text")) {
                                jsonAccumulator.append(part.path("text").asText());
                                chunkCount++;
                            }
                        }
                    }
                }
            }
        }

        long totalElapsed = System.currentTimeMillis() - start;
        System.out.printf("[\u2713 SSE stream received: %d chunk(s) in %dms]%n%n", chunkCount, totalElapsed);

        if (!sawAnyDataLine) {
            throw new IOException("Stream closed with zero SSE data lines \u2014 network drop or proxy truncation.");
        }

        // Distinguish truncation from schema mismatch instead of conflating them downstream.
        if ("MAX_TOKENS".equals(finishReason)) {
            throw new IOException("Generation truncated at maxOutputTokens (" + MAX_OUTPUT_TOKENS
                    + "). Raise the ceiling or reduce clause word budgets in SceneContract.");
        }
        if ("SAFETY".equals(finishReason) || "RECITATION".equals(finishReason)) {
            throw new IOException("Generation halted by the provider (finishReason=" + finishReason + ").");
        }

        String completeJson = jsonAccumulator.toString().trim();
        if (completeJson.isEmpty()) {
            throw new IOException("Zero JSON data accumulated despite " + chunkCount + " chunk(s) received.");
        }

        try {
            return objectMapper.readValue(completeJson, SceneContract.class);
        } catch (MismatchedInputException schemaMismatch) {
            throw new IOException("SceneContract deserialization failed (schema mismatch; stream completed with "
                    + "finishReason=" + (finishReason.isBlank() ? "STOP" : finishReason) + "): "
                    + schemaMismatch.getOriginalMessage(), schemaMismatch);
        }
    }

    // ---------------------------------------------------------------------
    // Input
    // ---------------------------------------------------------------------

    private String readBlockInput(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        int consecutiveEmptyLines = 0;

        while ((line = reader.readLine()) != null) {
            if (sb.isEmpty() && line.trim().startsWith(":")) {
                return line.trim();
            }
            if (line.trim().equalsIgnoreCase("END")) {
                break;
            }

            if (line.trim().isEmpty()) {
                consecutiveEmptyLines++;
                if (consecutiveEmptyLines >= 2 && !sb.isEmpty()) {
                    break;
                }
            } else {
                consecutiveEmptyLines = 0;
            }
            sb.append(line).append("\n");
        }
        return (line == null && sb.isEmpty()) ? null : sb.toString();
    }

    private String resolveSceneContent(String input) throws IOException {
        String cleanPath = input.replace("\"", "").replace("'", "").trim();
        if (cleanPath.toLowerCase().endsWith(".txt")) {
            try {
                Path path = Path.of(cleanPath);
                if (Files.exists(path) && Files.isRegularFile(path)) {
                    System.out.println("[Loading source file: " + path.toAbsolutePath() + "]");
                    return Files.readString(path, StandardCharsets.UTF_8);
                }
            } catch (InvalidPathException ignored) {
                // Fall through and treat the input as literal scene text.
            }
        }
        return input;
    }

    // ---------------------------------------------------------------------
    // Commands
    // ---------------------------------------------------------------------

    private boolean handleCommand(String input, BufferedReader reader) {
        String[] tokens = input.split("\\s+");
        String cmd = tokens[0].toLowerCase();
        String arg = tokens.length > 1 ? tokens[1].toLowerCase() : "";

        switch (cmd) {
            case ":exit", ":quit", ":q" -> {
                System.out.println("Terminating Visual Prompt Compiler.");
                return true;
            }
            case ":copy" -> {
                if (tokens.length > 1 && arg.matches("^[0-9]+$")) {
                    copyRegionalPassByIndex(Integer.parseInt(arg));
                } else if (lastOutput != null && !lastOutput.clipboardText().isBlank()) {
                    System.out.println(copyToClipboard(lastOutput.clipboardText())
                            ? "[\u2713] Copied to clipboard."
                            : "[!] Clipboard unavailable.");
                } else {
                    System.out.println("[!] No prompt to copy.");
                }
            }
            case ":regional", ":passes" -> handleRegionalCommand(arg, reader);
            case ":ar" -> {
                if (arg.matches("^[0-9]+:[0-9]+$")) {
                    currentAspectRatio = arg;
                    System.out.println("[\u2713] Default AR: " + currentAspectRatio);
                } else {
                    System.out.println("[!] Usage: :ar 16:9");
                }
            }
            case ":flags" -> {
                if (tokens.length > 1) {
                    customMjFlags = input.substring(tokens[0].length()).trim();
                    System.out.println("[\u2713] Flags set to: " + customMjFlags);
                } else {
                    System.out.println("[i] Current flags: " + customMjFlags);
                }
            }
            case ":engine" -> {
                if (arg.equals("mj") || arg.equals("midjourney")) {
                    activeEngine = PromptCompiler.EngineProfile.MIDJOURNEY_V6;
                } else if (arg.equals("flux") || arg.equals("flux1")) {
                    activeEngine = PromptCompiler.EngineProfile.FLUX_1_DEV;
                } else {
                    System.out.println("[!] Usage: :engine <mj|flux>");
                }
            }
            case ":mode" -> {
                switch (arg) {
                    case "direct" -> currentMode = IngestionMode.DIRECT;
                    case "narrative" -> currentMode = IngestionMode.NARRATIVE;
                    case "three", "3", "slots", "threestage", "three_stage" -> currentMode = IngestionMode.THREE_STAGE;
                    default -> System.out.println("[!] Usage: :mode <direct|narrative|three>");
                }
            }
            case ":narrative" -> currentMode = IngestionMode.NARRATIVE;
            case ":direct" -> currentMode = IngestionMode.DIRECT;
            case ":three", ":slots", ":threestage" -> currentMode = IngestionMode.THREE_STAGE;
            case ":model" -> {
                if (arg.equals("flash")) {
                    activeModel = GeminiModel.FLASH;
                } else if (arg.equals("pro")) {
                    activeModel = GeminiModel.PRO;
                } else {
                    System.out.println("[!] Usage: :model <flash|pro>");
                }
            }
            case ":help", ":h" -> printHelp();
            default -> System.out.println("[!] Unknown command. Try :help");
        }
        return false;
    }

    private void handleRegionalCommand(String arg, BufferedReader reader) {
        switch (arg) {
            case "on" -> {
                regionalPassesEnabled = true;
                System.out.println("[\u2713] Regional passes will be generated.");
            }
            case "off" -> {
                regionalPassesEnabled = false;
                System.out.println("[\u2713] Regional passes removed from the schema \u2014 no tokens spent on them.");
            }
            case "step" -> stepThroughRegionalPasses(reader);
            default -> {
                if (lastOutput != null) {
                    System.out.println(PromptCompiler.compileRegionalManifest(lastOutput));
                } else {
                    System.out.println("[!] Nothing compiled yet.");
                }
            }
        }
    }

    /** Reuses the caller's reader — a second BufferedReader over System.in steals buffered bytes. */
    private void stepThroughRegionalPasses(BufferedReader reader) {
        if (lastOutput == null || !lastOutput.hasRegionalPrompts()) {
            System.out.println("[!] No regional passes staged.");
            return;
        }
        List<PromptCompiler.RegionalPrompt> passes = lastOutput.regionalPrompts();
        for (int i = 0; i < passes.size(); i++) {
            System.out.printf("%n[PASS %d/%d] Zone: %s%nPrompt: %s%n",
                    i + 1, passes.size(), passes.get(i).zone(), passes.get(i).prompt());
            copyToClipboard(passes.get(i).prompt());
            if (i < passes.size() - 1) {
                System.out.println("  (Press Enter for next...)");
                try {
                    reader.readLine();
                } catch (IOException e) {
                    break;
                }
            }
        }
    }

    private void copyRegionalPassByIndex(int oneIndexed) {
        if (lastOutput == null || !lastOutput.hasRegionalPrompts()) {
            System.out.println("[!] No regional passes staged.");
            return;
        }
        List<PromptCompiler.RegionalPrompt> passes = lastOutput.regionalPrompts();
        int idx = oneIndexed - 1;
        if (idx < 0 || idx >= passes.size()) {
            System.out.println("[!] Pass " + oneIndexed + " out of range (1-" + passes.size() + ").");
            return;
        }
        System.out.println(copyToClipboard(passes.get(idx).prompt())
                ? "[\u2713] Copied pass " + oneIndexed + "."
                : "[!] Clipboard unavailable.");
    }

    private boolean copyToClipboard(String text) {
        try {
            if (GraphicsEnvironment.isHeadless()) {
                return false;
            }
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    // ---------------------------------------------------------------------
    // Console chrome
    // ---------------------------------------------------------------------

    private void printStatusLine() {
        System.out.printf("%n[%s | %s | AR: %s | Mode: %s | Regional: %s]%n",
                activeEngine.getDisplayName(), activeModel.getLabel(), currentAspectRatio,
                currentMode.name(), regionalPassesEnabled ? "ON" : "OFF");
    }

    private void printBanner() {
        System.out.println("===========================================================================");
        System.out.println("     VISUAL PROMPT COMPILER - TYPE-SAFE STRUCTURED OUTPUT PIPELINE        ");
        System.out.println("===========================================================================");
        System.out.println("[\u2713] GEMINI_API_KEY loaded from environment.");
    }

    private void printHelp() {
        System.out.println("Commands:");
        System.out.println("  :ar <w:h>              default aspect ratio");
        System.out.println("  :flags <flags>         Midjourney parameter string");
        System.out.println("  :engine <mj|flux>      target engine");
        System.out.println("  :model <flash|pro>     extraction model");
        System.out.println("  :mode <direct|narrative|three>");
        System.out.println("  :three                 shortcut for :mode three (3-slot payload contract)");
        System.out.println("  :copy [N]              copy last prompt, or regional pass N");
        System.out.println("  :regional <on|off|step>  on/off gates generation, not just display");
        System.out.println("  :exit");
    }
}