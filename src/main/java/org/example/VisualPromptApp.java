package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.GraphicsEnvironment;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisualPromptApp {

    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";
    private static final Pattern INLINE_AR_PATTERN = Pattern.compile("--ar\\s+([0-9]+:[0-9]+)", Pattern.CASE_INSENSITIVE);

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

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;

    private PromptCompiler.EngineProfile activeEngine = PromptCompiler.EngineProfile.MIDJOURNEY_V6;
    private GeminiModel activeModel = GeminiModel.FLASH;

    private String currentAspectRatio = "16:9";
    private String customMjFlags = "--style raw --v 6.1";
    private String lastCompiledPrompt = null;
    private SceneContract lastContract = null;
    private boolean regionalDisplayEnabled = true;

    public VisualPromptApp() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();

        String envKey = System.getenv("GEMINI_API_KEY");
        if (envKey != null && !envKey.isBlank()) {
            this.apiKey = envKey.trim();
        } else {
            this.apiKey = "key";
        }
    }

    public static void main(String[] args) {
        new VisualPromptApp().start();
    }

    public void start() {
        printBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            System.out.printf("\n[%s | %s | AR: %s | Regional: %s]%n",
                    activeEngine.getDisplayName(), activeModel.getLabel(), currentAspectRatio,
                    regionalDisplayEnabled ? "ON" : "OFF");
            System.out.println("> Enter scene description, .txt file path, or command (:help, :exit):");
            System.out.println("  (Type 'END' on a single line or press Enter twice to compile)");

            try {
                String input = readBlockInput(reader);
                if (input == null) {
                    System.out.println("\nExiting Visual Prompt Compiler.");
                    break;
                }

                String trimmed = input.trim();
                if (trimmed.isEmpty()) continue;

                if (trimmed.startsWith(":")) {
                    if (handleCommand(trimmed)) break;
                    continue;
                }

                String sceneText = resolveSceneContent(trimmed);
                String effectiveAr = currentAspectRatio;
                Matcher arMatcher = INLINE_AR_PATTERN.matcher(sceneText);
                if (arMatcher.find()) {
                    effectiveAr = arMatcher.group(1);
                    sceneText = arMatcher.replaceAll("").trim();
                    System.out.println("[✓] Detected inline aspect ratio override: --ar " + effectiveAr);
                }

                SceneContract contract = streamStructuredContractWithFeedbackAsync(sceneText);
                this.lastContract = contract;

                System.out.println(PromptCompiler.generateStructuralReport(contract));

                String dynamicMjFlags = "--ar " + effectiveAr + " " + customMjFlags;
                String finalPrompt = PromptCompiler.compile(contract, activeEngine, dynamicMjFlags);
                this.lastCompiledPrompt = finalPrompt;

                System.out.println("[PHASE 3: COMPILED DIFFUSION PROMPT]");
                System.out.println("---------------------------------------------------------------------------");
                System.out.println(finalPrompt);
                System.out.println("---------------------------------------------------------------------------");

                if (copyToClipboard(finalPrompt)) {
                    System.out.println("[✓] Compiled prompt copied directly to system clipboard.");
                } else {
                    System.out.println("[!] System clipboard unavailable (Headless environment).");
                }

                if (regionalDisplayEnabled && contract.regionalPasses() != null && !contract.regionalPasses().isEmpty()) {
                    System.out.println();
                    System.out.println(PromptCompiler.compileRegionalManifest(contract));
                    System.out.println("[i] Use :regional to re-print and step through these passes, or :copy N to copy pass N.");
                }

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

    private SceneContract streamStructuredContractWithFeedbackAsync(String rawScene) throws Exception {
        String endpointUrl = API_BASE_URL + activeModel.getEndpointId()
                + ":streamGenerateContent?alt=sse&key=" + apiKey;

        ObjectNode rootNode = objectMapper.createObjectNode();
        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text", "You are an analytical Physical Visual Staging Engine. Analyze the provided scene and extract strictly grounded physical parameters conforming to the requested JSON schema. Focus heavily on InteractionDynamics for multi-subject scenes.");

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", "Raw scene draft to structure:\n" + rawScene);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.set("response_schema", SceneContract.buildGeminiResponseSchema(objectMapper));
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 3000);

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();

        CompletableFuture<HttpResponse<Stream<String>>> futureResponse = httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofLines());

        long start = System.currentTimeMillis();
        Thread spinnerThread = new Thread(() -> {
            String[] spinnerChars = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
            int index = 0;
            try {
                while (!Thread.currentThread().isInterrupted()) {
                    double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                    System.out.printf("\r[Compiling SceneContract %s %.1fs elapsed] ", spinnerChars[index++ % spinnerChars.length], elapsed);
                    System.out.flush();
                    Thread.sleep(80);
                }
            } catch (InterruptedException ignored) {}
        });

        spinnerThread.setDaemon(true);
        spinnerThread.start();

        HttpResponse<Stream<String>> response;
        try {
            response = futureResponse.get();
        } finally {
            spinnerThread.interrupt();
        }

        if (response.statusCode() != 200) {
            String errorBody = response.body().collect(Collectors.joining("\n"));
            throw new IOException("Gemini API rejected request (HTTP " + response.statusCode() + "): " + errorBody);
        }

        StringBuilder jsonAccumulator = new StringBuilder();
        int chunkCount = 0;

        try (Stream<String> lines = response.body()) {
            Iterator<String> iterator = lines.iterator();
            while (iterator.hasNext()) {
                String line = iterator.next();
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || data.equals("[DONE]")) continue;

                    JsonNode root = objectMapper.readTree(data);
                    if (root.has("error")) throw new IOException("API Error: " + root.path("error").path("message").asText());

                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
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
        }

        long totalElapsed = System.currentTimeMillis() - start;
        System.out.print("\r" + " ".repeat(60) + "\r");
        System.out.printf("[✓ SSE Stream Received: %d chunks in %dms]%n%n", chunkCount, totalElapsed);

        String completeJson = jsonAccumulator.toString().trim();
        if (completeJson.isEmpty()) throw new IOException("Zero JSON data accumulated.");

        return objectMapper.readValue(completeJson, SceneContract.class);
    }

    private String readBlockInput(BufferedReader reader) throws IOException {
        StringBuilder sb = new StringBuilder();
        String line;
        int consecutiveEmptyLines = 0;

        while ((line = reader.readLine()) != null) {
            if (sb.isEmpty() && line.trim().startsWith(":")) return line.trim();
            if (line.trim().equalsIgnoreCase("END")) break;

            if (line.trim().isEmpty()) {
                consecutiveEmptyLines++;
                if (consecutiveEmptyLines >= 2 && !sb.isEmpty()) break;
            } else {
                consecutiveEmptyLines = 0;
            }
            sb.append(line).append("\n");
            if (System.console() == null && !reader.ready() && !sb.isEmpty()) break;
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
            } catch (InvalidPathException ignored) {}
        }
        return input;
    }

    private boolean handleCommand(String input) {
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
                } else if (lastCompiledPrompt != null && !lastCompiledPrompt.isBlank()) {
                    if (copyToClipboard(lastCompiledPrompt)) System.out.println("[✓] Copied to clipboard.");
                    else System.out.println("[!] Clipboard unavailable.");
                } else {
                    System.out.println("[!] No prompt to copy.");
                }
            }
            case ":regional", ":passes" -> handleRegionalCommand(arg);
            case ":ar" -> {
                if (arg.matches("^[0-9]+:[0-9]+$")) {
                    currentAspectRatio = arg;
                    System.out.println("[✓] Default AR updated to: " + currentAspectRatio);
                } else System.out.println("[!] Usage: :ar 16:9");
            }
            case ":flags" -> {
                if (tokens.length > 1) {
                    customMjFlags = input.substring(tokens[0].length()).trim();
                    System.out.println("[✓] Flags set to: " + customMjFlags);
                }
            }
            case ":engine", ":mode" -> {
                if (arg.equals("mj") || arg.equals("midjourney")) activeEngine = PromptCompiler.EngineProfile.MIDJOURNEY_V6;
                else if (arg.equals("flux") || arg.equals("flux1")) activeEngine = PromptCompiler.EngineProfile.FLUX_1_DEV;
            }
            case ":model" -> {
                if (arg.equals("flash")) activeModel = GeminiModel.FLASH;
                else if (arg.equals("pro")) activeModel = GeminiModel.PRO;
            }
            case ":help", ":h" -> printHelp();
            default -> System.out.println("[!] Unknown command.");
        }
        return false;
    }

    private void handleRegionalCommand(String arg) {
        if (arg.equals("on")) regionalDisplayEnabled = true;
        else if (arg.equals("off")) regionalDisplayEnabled = false;
        else if (arg.equals("step")) stepThroughRegionalPasses();
        else if (lastContract != null) System.out.println(PromptCompiler.compileRegionalManifest(lastContract));
    }

    private void stepThroughRegionalPasses() {
        if (lastContract == null || lastContract.regionalPasses() == null || lastContract.regionalPasses().isEmpty()) return;
        List<SceneContract.RegionalPass> passes = lastContract.regionalPasses();
        BufferedReader stepReader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        for (int i = 0; i < passes.size(); i++) {
            System.out.printf("%n[PASS %d/%d] Zone: %s%nPrompt: %s%n", i + 1, passes.size(), passes.get(i).targetZone(), passes.get(i).isolatedPrompt());
            copyToClipboard(passes.get(i).isolatedPrompt());
            if (i < passes.size() - 1) {
                System.out.println("  (Press Enter for next...)");
                try { stepReader.readLine(); } catch (IOException e) { break; }
            }
        }
    }

    private void copyRegionalPassByIndex(int oneIndexed) {
        if (lastContract == null || lastContract.regionalPasses() == null || lastContract.regionalPasses().isEmpty()) return;
        List<SceneContract.RegionalPass> passes = lastContract.regionalPasses();
        int idx = oneIndexed - 1;
        if (idx >= 0 && idx < passes.size()) copyToClipboard(passes.get(idx).isolatedPrompt());
    }

    private boolean copyToClipboard(String text) {
        try {
            if (GraphicsEnvironment.isHeadless()) return false;
            Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
            StringSelection selection = new StringSelection(text);
            clipboard.setContents(selection, selection);
            return true;
        } catch (Throwable t) {
            return false;
        }
    }

    private void printBanner() {
        System.out.println("===========================================================================");
        System.out.println("     VISUAL PROMPT COMPILER - TYPE-SAFE STRUCTURED OUTPUT PIPELINE        ");
        System.out.println("===========================================================================");
        if (apiKey != null) System.out.println("[✓] GEMINI_API_KEY loaded securely from environment.");
    }

    private void printHelp() {
        System.out.println("Help commands omitted for brevity.");
    }
}