package org.example;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.awt.*;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisualPromptApp {

    private static final String DEFAULT_API_KEY = "key";
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

    // Dynamic Flag Management
    private String currentAspectRatio = "16:9";
    private String customMjFlags = "--style raw --v 6.1";
    private String lastCompiledPrompt = null;

    public VisualPromptApp() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(20))
                .build();
        this.objectMapper = new ObjectMapper();

        String envKey = System.getenv("GEMINI_API_KEY");
        this.apiKey = (envKey != null && !envKey.isBlank()) ? envKey.trim() : DEFAULT_API_KEY;
    }

    public static void main(String[] args) {
        new VisualPromptApp().start();
    }

    public void start() {
        printBanner();
        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));

        while (true) {
            System.out.printf("\n[%s | %s | AR: %s]%n",
                    activeEngine.getDisplayName(), activeModel.getLabel(), currentAspectRatio);
            System.out.println("> Enter scene description, .txt file path, or command (:help, :exit):");
            System.out.println("  (Type 'END' on a single line or press Enter twice to compile)");

            try {
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
                    if (handleCommand(trimmed)) {
                        break;
                    }
                    continue;
                }

                String sceneText = resolveSceneContent(trimmed);

                // Detect inline aspect ratio override (e.g. "--ar 9:16")
                String effectiveAr = currentAspectRatio;
                Matcher arMatcher = INLINE_AR_PATTERN.matcher(sceneText);
                if (arMatcher.find()) {
                    effectiveAr = arMatcher.group(1);
                    sceneText = arMatcher.replaceAll("").trim(); // Clean flag from narrative text
                    System.out.println("[✓] Detected inline aspect ratio override: --ar " + effectiveAr);
                }

                // Stream evaluation with non-blocking UI ticker
                SceneContract contract = streamStructuredContractWithFeedback(sceneText);

                // Print structural analysis phase
                System.out.println(PromptCompiler.generateStructuralReport(contract));

                // Compile final prompt with dynamic flags
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
                    System.out.println("[!] System clipboard unavailable.");
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

    /**
     * Executes an SSE streaming request while maintaining an interactive terminal spinner.
     */
    private SceneContract streamStructuredContractWithFeedback(String rawScene) throws IOException, InterruptedException {
        String endpointUrl = API_BASE_URL + activeModel.getEndpointId()
                + ":streamGenerateContent?alt=sse&key=" + apiKey;

        ObjectNode rootNode = objectMapper.createObjectNode();

        // System instructions calibrated for subjectless scenes and fluid organic synthesis
        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text", """
                You are an analytical Physical Visual Staging Engine.
                Analyze the provided scene and extract strictly grounded physical parameters conforming to the requested JSON schema.
                
                STAGING & STRUCTURAL MANDATES:
                1. SUBJECTLESS & ENVIRONMENTAL SCENES:
                   - If the scene depicts an inanimate environment, architectural space, landscape, vehicle, or still life WITHOUT a primary character/figure, you MUST leave subjectStance null (omit it). Do NOT invent artificial humans or mannequins.
                   - If an active character, creature, or figure is present, populate subjectStance with dynamic rotational orientation and center of mass. Avoid static A-poses.
                2. PRIMARY SUBJECT OFFSET:
                   - Prefer 'LEFT_THIRD' or 'RIGHT_THIRD' as default tension axes for subjects and focal landmarks.
                   - Use 'CENTER_WEIGHTED' STRICTLY for intentional axial architectural symmetry (e.g. cathedral nave, Wes Anderson framing, one-point perspective corridor) or formal direct-stare portraits.
                3. KINETIC ANCHORS:
                   - Leave kineticAnchors null (omit it) unless an active dynamic physical force, beam, energy discharge, projectile, or violent collision is physically occurring. For dormant or static scenes, DO NOT hallucinate kinetic forces.
                4. ENVIRONMENTAL OPTICS:
                   - Derive light sources, rim lights, and shutter speeds authentically from the physical environment.
                5. FLUID PROMPT SYNTHESIS (midjourneyPrompt & fluxPrompt):
                   - Compose midjourneyPrompt and fluxPrompt with organic, dynamic sentence variation. AVOID rigid, boilerplate sentence sequencing (do NOT use identical opening formulas).
                   - midjourneyPrompt: High-density, cinematic descriptive English incorporating the staging, optics, and lighting. Do NOT append flags; flags are appended dynamically by the compiler.
                   - fluxPrompt: Comprehensive, tactile natural language prose optimized for Flux text-following.
                   - Strict ban on fluff: "photorealistic", "hyperrealistic", "8k", "16k", "masterpiece", "trending on artstation", "stunning", "breathtaking", "unreal engine".
                """);

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", "Raw scene draft to structure:\n" + rawScene);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("response_mime_type", "application/json");
        generationConfig.set("response_schema", SceneContract.buildGeminiResponseSchema(objectMapper));
        generationConfig.put("temperature", 0.2);
        generationConfig.put("maxOutputTokens", 2048);

        ArrayNode safetySettings = rootNode.putArray("safetySettings");
        String[] categories = {
                "HARM_CATEGORY_HARASSMENT",
                "HARM_CATEGORY_HATE_SPEECH",
                "HARM_CATEGORY_SEXUALLY_EXPLICIT",
                "HARM_CATEGORY_DANGEROUS_CONTENT"
        };
        for (String category : categories) {
            ObjectNode setting = safetySettings.addObject();
            setting.put("category", category);
            setting.put("threshold", "BLOCK_NONE");
        }

        String jsonPayload = objectMapper.writeValueAsString(rootNode);

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(endpointUrl))
                .header("Content-Type", "application/json")
                .header("Accept", "text/event-stream")
                .POST(HttpRequest.BodyPublishers.ofString(jsonPayload, StandardCharsets.UTF_8))
                .timeout(Duration.ofSeconds(90))
                .build();

        AtomicBoolean isStreaming = new AtomicBoolean(true);
        Thread spinnerThread = new Thread(() -> {
            String[] spinnerChars = {"⠋", "⠙", "⠹", "⠸", "⠼", "⠴", "⠦", "⠧", "⠇", "⠏"};
            int index = 0;
            long start = System.currentTimeMillis();
            while (isStreaming.get()) {
                double elapsed = (System.currentTimeMillis() - start) / 1000.0;
                System.out.printf("\r[Compiling SceneContract %s %.1fs elapsed] ", spinnerChars[index++ % spinnerChars.length], elapsed);
                System.out.flush();
                try {
                    Thread.sleep(80);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        spinnerThread.setDaemon(true);
        spinnerThread.start();

        StringBuilder jsonAccumulator = new StringBuilder();
        long startTime = System.currentTimeMillis();
        int chunkCount = 0;

        try {
            HttpResponse<Stream<String>> response = httpClient.send(request, HttpResponse.BodyHandlers.ofLines());

            if (response.statusCode() != 200) {
                String errorBody = response.body().collect(Collectors.joining("\n"));
                throw new IOException("Gemini API rejected request (HTTP " + response.statusCode() + "): " + errorBody);
            }

            try (Stream<String> lines = response.body()) {
                Iterator<String> iterator = lines.iterator();
                while (iterator.hasNext()) {
                    String line = iterator.next();
                    if (line.startsWith("data:")) {
                        String data = line.substring(5).trim();
                        if (data.isEmpty() || data.equals("[DONE]")) {
                            continue;
                        }

                        JsonNode root = objectMapper.readTree(data);
                        if (root.has("error")) {
                            throw new IOException("API Streaming Error: " + root.path("error").path("message").asText());
                        }

                        JsonNode candidates = root.path("candidates");
                        if (candidates.isArray() && !candidates.isEmpty()) {
                            JsonNode parts = candidates.get(0).path("content").path("parts");
                            if (parts.isArray() && !parts.isEmpty()) {
                                for (JsonNode part : parts) {
                                    if (part.has("text")) {
                                        String chunk = part.path("text").asText();
                                        if (!chunk.isEmpty()) {
                                            jsonAccumulator.append(chunk);
                                            chunkCount++;
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            isStreaming.set(false);
            spinnerThread.interrupt();
            long totalElapsed = System.currentTimeMillis() - startTime;
            System.out.print("\r" + " ".repeat(60) + "\r");
            System.out.printf("[✓ SSE Stream Received: %d chunks in %dms]%n%n", chunkCount, totalElapsed);
        }

        String completeJson = jsonAccumulator.toString().trim();
        if (completeJson.isEmpty()) {
            throw new IOException("Zero JSON data accumulated from SSE stream.");
        }

        return objectMapper.readValue(completeJson, SceneContract.class);
    }

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

            if (System.console() == null && !reader.ready() && !sb.isEmpty()) {
                break;
            }
        }

        if (line == null && sb.isEmpty()) {
            return null;
        }

        return sb.toString();
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
            }
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
                if (lastCompiledPrompt != null && !lastCompiledPrompt.isBlank()) {
                    if (copyToClipboard(lastCompiledPrompt)) {
                        System.out.println("[✓] Last compiled prompt re-copied to clipboard.");
                    } else {
                        System.out.println("[!] Clipboard unavailable.");
                    }
                } else {
                    System.out.println("[!] No compiled prompt available to copy yet.");
                }
            }
            case ":ar" -> {
                if (arg.matches("^[0-9]+:[0-9]+$")) {
                    currentAspectRatio = arg;
                    System.out.println("[✓] Default aspect ratio updated to: " + currentAspectRatio);
                } else {
                    System.out.println("[!] Invalid aspect ratio format. Usage: :ar 16:9, :ar 9:16, :ar 1:1");
                }
            }
            case ":flags" -> {
                if (tokens.length > 1) {
                    customMjFlags = input.substring(tokens[0].length()).trim();
                    System.out.println("[✓] Custom Midjourney flags set to: " + customMjFlags);
                } else {
                    System.out.println("[!] Usage: :flags --style raw --v 6.1");
                }
            }
            case ":engine", ":mode" -> {
                if (arg.equals("mj") || arg.equals("midjourney")) {
                    activeEngine = PromptCompiler.EngineProfile.MIDJOURNEY_V6;
                    System.out.println("[✓] Target engine switched to: Midjourney v6.1");
                } else if (arg.equals("flux") || arg.equals("flux1")) {
                    activeEngine = PromptCompiler.EngineProfile.FLUX_1_DEV;
                    System.out.println("[✓] Target engine switched to: Flux.1-Dev");
                } else {
                    System.out.println("[!] Invalid engine. Usage: :engine [mj|flux]");
                }
            }
            case ":model" -> {
                if (arg.equals("flash")) {
                    activeModel = GeminiModel.FLASH;
                    System.out.println("[✓] Model switched to: " + activeModel.getLabel());
                } else if (arg.equals("pro")) {
                    activeModel = GeminiModel.PRO;
                    System.out.println("[✓] Model switched to: " + activeModel.getLabel());
                } else {
                    System.out.println("[!] Invalid model. Usage: :model [flash|pro]");
                }
            }
            case ":help", ":h" -> printHelp();
            default -> System.out.println("[!] Unknown command: " + cmd + ". Type :help for command reference.");
        }
        return false;
    }

    private boolean copyToClipboard(String text) {
        if (GraphicsEnvironment.isHeadless()) {
            return false;
        }
        try {
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
        System.out.println("  Universal Polymorphic Schema | Organic Synthesis | Dynamic Flags         ");
        System.out.println("===========================================================================");
        if (apiKey.equals(DEFAULT_API_KEY)) {
            System.out.println("[✓] Using configured fallback API key.");
        } else {
            System.out.println("[✓] GEMINI_API_KEY loaded from environment.");
        }
    }

    private void printHelp() {
        System.out.println("""
                Available Commands:
                  :ar [ratio]         Set default aspect ratio (e.g., :ar 16:9, :ar 9:16, :ar 1:1)
                  :flags [string]     Configure custom Midjourney flags (e.g., :flags --style raw --v 6.1)
                  :engine [mj|flux]   Switch diffusion target (Midjourney v6.1 vs Flux.1-Dev)
                  :mode [mj|flux]     Alias for :engine
                  :model [flash|pro]  Switch LLM backend (gemini-3.6-flash vs gemini-3.1-pro)
                  :copy               Re-copy the last generated prompt to clipboard
                  :help, :h           Display this reference guide
                  :exit, :quit, :q    Terminate the application
                
                Input Protocol:
                  - Multi-line scene drafts: paste text, then type 'END' on a single line or press Enter twice.
                  - Inline aspect ratio: include '--ar <ratio>' anywhere in your scene text to override.
                  - Local file: provide the path to a UTF-8 text file (e.g., scene.txt).
                """);
    }
}