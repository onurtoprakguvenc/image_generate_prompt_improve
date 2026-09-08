package org.example;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

public class PromptCompiler {

    public enum EngineProfile {
        MIDJOURNEY_V6("Midjourney v6.1"),
        FLUX_1_DEV("Flux.1-Dev");

        private final String displayName;

        EngineProfile(String displayName) {
            this.displayName = displayName;
        }

        public String getDisplayName() {
            return displayName;
        }
    }

    private static final List<String> BANNED_TOKENS = List.of(
            "photorealistic", "hyperrealistic", "ultra-realistic", "8k", "16k",
            "masterpiece", "trending on artstation", "trending", "stunning",
            "breathtaking", "unreal engine", "award winning", "octane render"
    );

    private static final List<Pattern> BANNED_PATTERNS = BANNED_TOKENS.stream()
            .map(token -> Pattern.compile("\\b" + Pattern.quote(token) + "\\b", Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList());

    // Non-greedy, lookbehind-guarded parameter cleanup (handles multi-word flag values without swallowing downstream text)
    private static final Pattern PARAMETER_CLEANUP = Pattern.compile(
            "(?<![-\\w])(--ar|--style|--v|--chaos|--weird|--stylize|--no)\\s+((?:(?!--)\\S+)(?:\\s+(?:(?!--)\\S+))?)",
            Pattern.CASE_INSENSITIVE
    );
    private static final Pattern PUNCTUATION_CLEANUP = Pattern.compile("[-–—\\s,;]+$");

    // Expanded conjunction stop-set with a 120-character bounded match to prevent runaway clause ingestion
    private static final String STOP_CONJUNCTIONS =
            "\\bbecause\\b|\\bdue to\\b|\\binstead\\b|\\bas\\b|\\bwhile\\b|\\bwhen\\b|\\bso that\\b|\\bin order to\\b";

    private static final Pattern NEGATIVE_AVOID_PATTERN = Pattern.compile(
            "(?i)\\bavoid\\s+((?:(?!" + STOP_CONJUNCTIONS + ")[^;.,]){1,120})"
    );
    private static final Pattern NEGATIVE_WITHOUT_PATTERN = Pattern.compile(
            "(?i)\\bwithout\\s+((?:(?!" + STOP_CONJUNCTIONS + ")[^;.,]){1,120})"
    );
    private static final Pattern NEGATIVE_DO_NOT_PATTERN = Pattern.compile(
            "(?i)\\bdo\\s+not\\s+((?:(?!" + STOP_CONJUNCTIONS + ")[^;.,]){1,120})"
    );

    public static String compile(SceneContract contract, EngineProfile profile, String dynamicFlags) {
        Objects.requireNonNull(contract, "SceneContract cannot be null");
        Objects.requireNonNull(profile, "EngineProfile cannot be null");

        return switch (profile) {
            case MIDJOURNEY_V6 -> compileMidjourney(contract, dynamicFlags);
            case FLUX_1_DEV -> compileFlux(contract);
        };
    }

    private static String compileMidjourney(SceneContract c, String flags) {
        String prose = safe(c.midjourneyPrompt(), c.dramaticAction());

        prose = weaveInteractionDirective(prose, c.interactionDynamics());
        prose = weaveSurrealMounting(prose, c.surrealMountings());

        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.isEmpty() && !base.endsWith(".")) {
            base += ".";
        }

        String sanitized = sanitizeBannedTokens(base);
        sanitized = sanitizeNegativeParadox(sanitized);

        String cleanFlags = (flags != null && !flags.isBlank()) ? flags.trim() : "--ar 16:9 --style raw --v 6.1";

        // Positive Geometric Saturation model: No hardcoded generic negative strings are injected.
        // Negative parameters are strictly limited to genuine displaced items from SurrealMounting.
        if (c.surrealMountings() != null && !c.surrealMountings().isEmpty()) {
            String surrealNegatives = c.surrealMountings().stream()
                    .filter(Objects::nonNull)
                    .map(SceneContract.SurrealMounting::requiredNegatives)
                    .filter(n -> n != null && !n.isBlank())
                    .collect(Collectors.joining(", "));

            if (!surrealNegatives.isEmpty()) {
                if (cleanFlags.toLowerCase().contains("--no ")) {
                    cleanFlags += ", " + surrealNegatives;
                } else {
                    cleanFlags += " --no " + surrealNegatives;
                }
            }
        }

        return (sanitized + " " + cleanFlags).trim();
    }

    private static String compileFlux(SceneContract c) {
        String prose = safe(c.fluxPrompt(), c.dramaticAction());

        prose = weaveInteractionDirective(prose, c.interactionDynamics());
        prose = weaveSurrealMounting(prose, c.surrealMountings());

        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.isEmpty() && !base.endsWith(".")) {
            base += ".";
        }

        String sanitized = sanitizeBannedTokens(base);
        return sanitizeNegativeParadox(sanitized);
    }

    private static String weaveInteractionDirective(String prose, SceneContract.InteractionDynamics dynamics) {
        String base = prose == null ? "" : prose;
        if (dynamics == null) {
            return base;
        }
        String trimmed = base.trim();
        if (!trimmed.isEmpty() && !trimmed.endsWith(".") && !trimmed.endsWith(",")) {
            trimmed += ".";
        }

        String contactPoint = safe(dynamics.contactPointCoordinate(), "the primary contact zone");
        String tensionVector = safe(dynamics.mutualTensionVector(), "sustained mutual force");
        String positiveEnforcement = sanitizeNegativeParadox(safe(dynamics.primaryFocalFailureRisk(), ""));

        if (positiveEnforcement.isBlank()) {
            return trimmed + String.format(
                    " Physical contact is structurally locked at %s, %s.",
                    contactPoint, tensionVector
            );
        }

        return trimmed + String.format(
                " Physical contact is structurally locked at %s, %s, explicitly enforcing %s.",
                contactPoint, tensionVector, positiveEnforcement
        );
    }

    private static String weaveSurrealMounting(String prose, List<SceneContract.SurrealMounting> mountings) {
        if (mountings == null || mountings.isEmpty()) {
            return prose == null ? "" : prose;
        }
        String trimmed = (prose == null ? "" : prose).trim();
        if (!trimmed.isEmpty() && !trimmed.endsWith(".") && !trimmed.endsWith(",")) {
            trimmed += ".";
        }

        StringBuilder clause = new StringBuilder();
        for (SceneContract.SurrealMounting m : mountings) {
            if (m == null) continue;
            String noun = safe(m.originalNoun(), "element");
            String primitive = safe(m.geometricPrimitive(), "surface-bounded volume");
            String verb = safe(m.surfaceMountingVerb(), "mechanically locked to the plane");
            clause.append(String.format(" The %s is decoupled from standard gravity: resolving purely as a %s, structurally locked via %s.",
                    noun, primitive, verb));
        }

        return trimmed + clause.toString();
    }

    private static String sanitizeNegativeParadox(String input) {
        if (input == null || input.isBlank()) return "";

        String result = input;
        result = NEGATIVE_AVOID_PATTERN.matcher(result).replaceAll("actively suppressing $1 through explicit mechanical force");
        result = NEGATIVE_WITHOUT_PATTERN.matcher(result).replaceAll("maintaining unbroken contact against $1");
        result = NEGATIVE_DO_NOT_PATTERN.matcher(result).replaceAll("countering $1 with rigid physical displacement");

        result = result.replaceAll("(?i)\\bsuperficial\\s+hand\\s+placement\\b", "deep tissue compression and mechanical bone-to-bone grip")
                .replaceAll("(?i)\\bhovering\\s+hands?\\b", "fingers physically depressing and sinking into skin")
                .replaceAll("\\s{2,}", " ");

        return result.trim();
    }

    public static String compileRegionalManifest(SceneContract contract) {
        Objects.requireNonNull(contract, "SceneContract cannot be null");

        List<SceneContract.RegionalPass> passes = contract.regionalPasses();
        if (passes == null || passes.isEmpty()) {
            return "[No regional passes staged for this scene — single-subject or non-contact composition.]";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("===========================================================================\n");
        sb.append("  REGIONAL / INPAINT MANIFEST\n");
        sb.append("===========================================================================\n");

        int index = 1;
        for (SceneContract.RegionalPass pass : passes) {
            if (pass == null) {
                index++;
                continue;
            }
            String isolatedRaw = safe(pass.isolatedPrompt(), "[missing isolated prompt]");
            String isolated = sanitizeBannedTokens(isolatedRaw.trim());
            isolated = sanitizeNegativeParadox(isolated);
            sb.append(String.format("""
                    [PASS %d] Zone: %s
                      Bounding   : %s
                      Prompt     : %s
                    ---------------------------------------------------------------------------
                    """,
                    index,
                    pass.targetZone() != null ? pass.targetZone() : "[unspecified]",
                    safe(pass.boundingDescription(), "[unspecified]"),
                    isolated
            ));
            index++;
        }

        return sb.toString().stripTrailing();
    }

    public static String generateStructuralReport(SceneContract c) {
        Objects.requireNonNull(c, "SceneContract cannot be null");
        Objects.requireNonNull(c.cameraRig(), "cameraRig is required by schema but was null");
        Objects.requireNonNull(c.environmentalOptics(), "environmentalOptics is required by schema but was null");

        String stanceReport = c.subjectStance() == null
                ? "  - Subject Stance  : [None - Subjectless Scene / Inanimate Environment]\n"
                : String.format("""
                  - Stance & Vector : %s | %s
                  - Center of Mass  : %s
                """,
                safe(c.subjectStance().facingVector(), "[unspecified]"),
                safe(c.subjectStance().poseDynamics(), "[unspecified]"),
                safe(c.subjectStance().centerOfGravity(), "[unspecified]"));

        String kineticReport = c.kineticAnchors() == null
                ? "  - Kinetic Anchors : [None - Static Scene]\n"
                : String.format("""
                  - Physical Origin : %s
                  - Kinetic Vector  : %s
                  - Impact Area     : %s
                """,
                safe(c.kineticAnchors().exactOriginPoint(), "[unspecified]"),
                safe(c.kineticAnchors().forceTrajectory(), "[unspecified]"),
                safe(c.kineticAnchors().physicalImpactArea(), "[unspecified]"));

        String surrealReport = (c.surrealMountings() == null || c.surrealMountings().isEmpty())
                ? "  - Surreal Mounts  : [None]\n"
                : "  - Surreal Mounts  :\n" + c.surrealMountings().stream()
                .filter(Objects::nonNull)
                .map(m -> String.format("      * %s -> %s [Negative: %s]",
                        safe(m.originalNoun(), "[unspecified]"),
                        safe(m.geometricPrimitive(), "[unspecified]"),
                        safe(m.requiredNegatives(), "[none]")))
                .collect(Collectors.joining("\n")) + "\n";

        String phase4;
        boolean hasRegional = c.regionalPasses() != null && !c.regionalPasses().isEmpty();
        if (c.interactionDynamics() == null && !hasRegional) {
            phase4 = "";
        } else {
            StringBuilder p4 = new StringBuilder();
            p4.append("\n[PHASE 4: INTERACTION DYNAMICS & REGIONAL MASKS]\n");

            if (c.interactionDynamics() != null) {
                SceneContract.InteractionDynamics id = c.interactionDynamics();
                p4.append(String.format("""
                          - Contact Point   : %s
                          - Tension Vector  : %s
                          - Enforced Lock   : %s
                        """,
                        safe(id.contactPointCoordinate(), "[unspecified]"),
                        safe(id.mutualTensionVector(), "[unspecified]"),
                        sanitizeNegativeParadox(safe(id.primaryFocalFailureRisk(), ""))
                ));
            } else {
                p4.append("  - Interaction Dynamics : [None]\n");
            }

            if (hasRegional) {
                p4.append("  - Regional Passes :\n");
                int idx = 1;
                for (SceneContract.RegionalPass pass : c.regionalPasses()) {
                    if (pass == null) { idx++; continue; }
                    p4.append(String.format(
                            "      [%d] %s -> %s%n          Prompt: %s%n",
                            idx,
                            pass.targetZone() != null ? pass.targetZone() : "[unspecified]",
                            safe(pass.boundingDescription(), "[unspecified]"),
                            sanitizeNegativeParadox(safe(pass.isolatedPrompt(), ""))
                    ));
                    idx++;
                }
            } else {
                p4.append("  - Regional Passes : [None staged]\n");
            }

            phase4 = p4.toString();
        }

        return String.format("""
                [PHASE 1: SPATIAL BLOCKING & COMPOSITION]
                  - Action Summary  : %s
                  - Viewport & Lens : %s | %s (%s)
                  - Frame Offset    : %s
                %s
                [PHASE 2: KINETICS & ENVIRONMENTAL OPTICS]
                %s%s  - Primary Light   : %s
                  - Edge Rim Light  : %s
                  - Shutter & Atmos : %s | %s
                %s""",
                safe(c.dramaticAction(), "[unspecified]"),
                safe(c.cameraRig().viewportAngle(), "[unspecified]"),
                safe(c.cameraRig().focalLength(), "[unspecified]"),
                safe(c.cameraRig().cameraDistance(), "[unspecified]"),
                c.cameraRig().primarySubjectOffset() != null ? c.cameraRig().primarySubjectOffset() : "[unspecified]",
                stanceReport,
                kineticReport,
                surrealReport,
                safe(c.environmentalOptics().primaryLightSource(), "[unspecified]"),
                safe(c.environmentalOptics().rimLight(), "[unspecified]"),
                safe(c.environmentalOptics().shutterSpeed(), "[unspecified]"),
                safe(c.environmentalOptics().atmosphericParticulates(), "[unspecified]"),
                phase4
        );
    }

    private static String sanitizeBannedTokens(String input) {
        if (input == null) return "";
        String sanitized = input;
        for (Pattern pattern : BANNED_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("").replaceAll("\\s{2,}", " ");
        }
        return sanitized.trim();
    }

    private static String safe(String value, String fallback) {
        return (value == null || value.isBlank()) ? fallback : value;
    }
}