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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class VisualPromptApp {

    private static final String DEFAULT_API_KEY = "key";
    private static final String API_BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models/";

    public enum EngineProfile {
        MIDJOURNEY_V6("Midjourney v6.1", """
                You are the VISUAL PROMPT COMPILER targeting Midjourney v6.1.
                Execute a strict 3-Phase Sequential Pipeline before producing the output.
                
                PHASE 1: SPATIAL BLOCKING & PHYSICS ISOLATION
                - Define metric spatial staging: strictly establish metric distances between entities (e.g., minimum 6-10 meters between shooter and target to prevent frame cramping).
                - Define depth tiers: Foreground, Midground, Background.
                - Resolve physical mechanics: distinguish between moving sub-elements and rigid structures (e.g., motor chassis stays level on a linear vector; rotational motion is strictly isolated to cutting teeth).
                
                PHASE 2: ILLUMINATION & OPTICAL CALIBRATION
                - Source light strictly from the scene context (e.g., cold industrial skylights, muzzle burst glare, tungsten dust scatter).
                - Dictate shutter dynamics (e.g., 1/2000s freeze vs. motion blur) and aperture/depth of field matching Phase 1 staging.
                
                PHASE 3: TARGET DIFFUSION SYNTHESIS
                - Synthesize the verified physics and optics into a single cohesive, complete prose paragraph.
                - Drop zero subjects: every entity, weapon, and environmental anchor must be retained.
                - Banned words: photorealistic, hyperrealistic, 8k, 16k, masterpiece, trending on artstation, unreal engine.
                - Append strictly a single space followed by: --ar 16:9 --style raw --v 6.1
                
                OUTPUT FORMAT:
                You must output the stages explicitly using these exact headers:
                [PHASE 1: SPATIAL & PHYSICS]
                <concise coordinate and mechanical breakdown>
                
                [PHASE 2: ILLUMINATION & OPTICS]
                <concise lighting and optical specs>
                
                [FINAL PROMPT]
                <single complete prompt ending with --ar 16:9 --style raw --v 6.1>
                """),

        FLUX_1_DEV("Flux.1-Dev", """
                You are the VISUAL PROMPT COMPILER targeting Flux.1-Dev.
                Execute a strict 3-Phase Sequential Pipeline before producing the output.
                
                PHASE 1: SPATIAL BLOCKING & PHYSICS ISOLATION
                - Define metric spatial staging: strictly establish metric distances between entities to prevent frame cramping.
                - Define depth tiers: Foreground, Midground, Background.
                - Resolve physical mechanics: distinguish linear trajectory from internal moving mechanisms.
                
                PHASE 2: ILLUMINATION & OPTICAL CALIBRATION
                - Source light strictly from environmental realism (direct beam, spill, falloff).
                - Dictate shutter speed and surface material interactions (dust, sparks, fractures).
                
                PHASE 3: TARGET DIFFUSION SYNTHESIS
                - Synthesize into a single high-fidelity English prose paragraph.
                - Do NOT include any Midjourney parameters (no --ar, no --style, no --v).
                - Banned words: photorealistic, hyperrealistic, 8k, 16k, masterpiece, trending on artstation, unreal engine.
                
                OUTPUT FORMAT:
                You must output the stages explicitly using these exact headers:
                [PHASE 1: SPATIAL & PHYSICS]
                <concise coordinate and mechanical breakdown>
                
                [PHASE 2: ILLUMINATION & OPTICS]
                <concise lighting and optical specs>
                
                [FINAL PROMPT]
                <pure descriptive paragraph without flags>
                """);

        private final String displayName;
        private final String systemInstruction;

        EngineProfile(String displayName, String systemInstruction) {
            this.displayName = displayName;
            this.systemInstruction = systemInstruction;
        }

        public String getDisplayName() {
            return displayName;
        }

        public String getSystemInstruction() {
            return systemInstruction;
        }
    }

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

    private EngineProfile activeEngine = EngineProfile.MIDJOURNEY_V6;
    private GeminiModel activeModel = GeminiModel.FLASH;
    private String lastCompiledPrompt = null;

    public VisualPromptApp() {
        this.httpClient = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(Duration.ofSeconds(15))
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
            System.out.printf("\n[%s | %s]%n", activeEngine.getDisplayName(), activeModel.getLabel());
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

                System.out.printf("%n[Streaming Pipeline from %s for %s target...]%n%n",
                        activeModel.getEndpointId(), activeEngine.getDisplayName());

                String fullOutput = streamCompilePrompt(sceneText);
                String extractedPrompt = extractFinalPrompt(fullOutput);
                this.lastCompiledPrompt = extractedPrompt;

                System.out.println("\n");
                boolean copied = copyToClipboard(extractedPrompt);
                if (copied) {
                    System.out.println("[✓] Target prompt [FINAL PROMPT] isolated and copied to system clipboard.");
                } else {
                    System.out.println("[!] Clipboard unavailable in this environment.");
                }

            } catch (IOException e) {
                System.err.println("\n[I/O Error] " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                System.err.println("\n[Thread Interrupted] " + e.getMessage());
                break;
            } catch (Exception e) {
                System.err.println("\n[Engine Error] " + e.getMessage());
            }
        }
    }

    private String streamCompilePrompt(String rawScene) throws IOException, InterruptedException {
        String endpointUrl = API_BASE_URL + activeModel.getEndpointId()
                + ":streamGenerateContent?alt=sse&key=" + apiKey;

        ObjectNode rootNode = objectMapper.createObjectNode();

        ObjectNode systemInstructionNode = rootNode.putObject("systemInstruction");
        ArrayNode sysParts = systemInstructionNode.putArray("parts");
        sysParts.addObject().put("text", activeEngine.getSystemInstruction());

        ArrayNode contentsArray = rootNode.putArray("contents");
        ObjectNode contentObj = contentsArray.addObject();
        contentObj.put("role", "user");
        ArrayNode userParts = contentObj.putArray("parts");
        userParts.addObject().put("text", "Compile this scene through Phase 1, Phase 2, and Final Prompt synthesis:\n" + rawScene);

        ObjectNode generationConfig = rootNode.putObject("generationConfig");
        generationConfig.put("temperature", 0.3);
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
                .timeout(Duration.ofSeconds(60))
                .build();

        long startTime = System.currentTimeMillis();
        long[] ttftHolder = new long[]{-1};
        StringBuilder promptAccumulator = new StringBuilder();

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
                                        if (ttftHolder[0] == -1) {
                                            ttftHolder[0] = System.currentTimeMillis() - startTime;
                                        }
                                        System.out.print(chunk);
                                        System.out.flush();
                                        promptAccumulator.append(chunk);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        long totalDuration = System.currentTimeMillis() - startTime;
        long ttft = ttftHolder[0] == -1 ? totalDuration : ttftHolder[0];

        System.out.printf("%n%n[Metrics: TTFT = %dms | Total Duration = %dms]", ttft, totalDuration);
        return promptAccumulator.toString().trim();
    }

    private String extractFinalPrompt(String rawOutput) {
        Pattern pattern = Pattern.compile("\\[FINAL PROMPT\\]\\s*([\\s\\S]+)$", Pattern.CASE_INSENSITIVE);
        Matcher matcher = pattern.matcher(rawOutput);
        String finalPrompt;
        if (matcher.find()) {
            finalPrompt = matcher.group(1).trim();
        } else {
            finalPrompt = rawOutput.trim();
        }

        if (activeEngine == EngineProfile.MIDJOURNEY_V6 && !finalPrompt.contains("--v 6")) {
            finalPrompt = finalPrompt.replaceAll("[-–—\\s,;]+$", "").trim();
            finalPrompt += " --ar 16:9 --style raw --v 6.1";
        }
        return finalPrompt;
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
            case ":engine", ":mode" -> {
                if (arg.equals("mj") || arg.equals("midjourney")) {
                    activeEngine = EngineProfile.MIDJOURNEY_V6;
                    System.out.println("[✓] Target engine switched to: Midjourney v6.1");
                } else if (arg.equals("flux") || arg.equals("flux1")) {
                    activeEngine = EngineProfile.FLUX_1_DEV;
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
        System.out.println("      VISUAL PROMPT COMPILER - SEQUENTIAL PIPELINE (PHASE 1-3)            ");
        System.out.println("  Spatial Physics | Optical Calibration | Isolated Prompt Extraction       ");
        System.out.println("===========================================================================");
        if (apiKey.equals(DEFAULT_API_KEY)) {
            System.out.println("[✓] API key configured via fallback constant.");
        } else {
            System.out.println("[✓] GEMINI_API_KEY detected from environment.");
        }
    }

    private void printHelp() {
        System.out.println("""
                Available Commands:
                  :engine [mj|flux]   Switch diffusion target (Midjourney v6.1 vs Flux.1-Dev)
                  :mode [mj|flux]     Alias for :engine
                  :model [flash|pro]  Switch LLM backend (gemini-3.6-flash vs gemini-3.1-pro)
                  :copy               Re-copy the last generated prompt to clipboard
                  :help, :h           Display this reference manual
                  :exit, :quit, :q    Terminate the application
                
                Input Protocol:
                  - Multi-line scene drafts: paste text, then type 'END' on a single line or press Enter twice.
                  - Local file: provide the path to a UTF-8 text file (e.g., scene.txt).
                """);
    }
}