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

    // Pre-compiled regex patterns to eliminate CPU overhead during prompt synthesis
    private static final List<Pattern> BANNED_PATTERNS = BANNED_TOKENS.stream()
            .map(token -> Pattern.compile("\\b" + Pattern.quote(token) + "\\b", Pattern.CASE_INSENSITIVE))
            .collect(Collectors.toList());

    private static final Pattern PARAMETER_CLEANUP = Pattern.compile("(--ar|--style|--v|--chaos|--weird|--stylize).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUNCTUATION_CLEANUP = Pattern.compile("[-–—\\s,;]+$");

    // İYİLEŞTİRME 1: Negatif Paradoks Regex Temizleyicileri
    private static final Pattern NEGATIVE_AVOID_PATTERN = Pattern.compile("(?i)\\bavoid\\s+([^;.,]+)(?:;|,|\\.)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_WITHOUT_PATTERN = Pattern.compile("(?i)\\bwithout\\s+([^;.,]+)(?:;|,|\\.)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern NEGATIVE_DO_NOT_PATTERN = Pattern.compile("(?i)\\bdo\\s+not\\s+([^;.,]+)(?:;|,|\\.)?", Pattern.CASE_INSENSITIVE);

    public static String compile(SceneContract contract, EngineProfile profile, String dynamicFlags) {
        Objects.requireNonNull(contract, "SceneContract cannot be null");
        Objects.requireNonNull(profile, "EngineProfile cannot be null");

        return switch (profile) {
            case MIDJOURNEY_V6 -> compileMidjourney(contract, dynamicFlags);
            case FLUX_1_DEV -> compileFlux(contract);
        };
    }

    private static String compileMidjourney(SceneContract c, String flags) {
        String prose = (c.midjourneyPrompt() != null && !c.midjourneyPrompt().isBlank())
                ? c.midjourneyPrompt()
                : c.dramaticAction();

        // Kinetik temas direktifini bağla ve negatif paradoksu filtrele
        prose = weaveInteractionDirective(prose, c.interactionDynamics());

        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.endsWith(".")) {
            base += ".";
        }

        String sanitized = sanitizeBannedTokens(base);
        sanitized = sanitizeNegativeParadox(sanitized);

        String cleanFlags = (flags != null && !flags.isBlank()) ? flags.trim() : "--ar 16:9 --style raw --v 6.1";

        return sanitized + " " + cleanFlags;
    }

    private static String compileFlux(SceneContract c) {
        String prose = (c.fluxPrompt() != null && !c.fluxPrompt().isBlank())
                ? c.fluxPrompt()
                : c.dramaticAction();

        prose = weaveInteractionDirective(prose, c.interactionDynamics());

        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.endsWith(".")) {
            base += ".";
        }

        String sanitized = sanitizeBannedTokens(base);
        return sanitizeNegativeParadox(sanitized);
    }

    /**
     * İYİLEŞTİRME 2: weaveInteractionDirective Güncellemesi
     * "with avoid superficial hand placement" kalıbını tamamen ortadan kaldırır.
     * Negatif risk uyarısını difüzyonun anladığı zorunlu pozitif temas komutuna dönüştürür.
     */
    private static String weaveInteractionDirective(String prose, SceneContract.InteractionDynamics dynamics) {
        if (dynamics == null) {
            return prose;
        }
        String trimmed = prose.trim();
        if (!trimmed.isEmpty() && !trimmed.endsWith(".") && !trimmed.endsWith(",")) {
            trimmed += ".";
        }

        // Gemini'nin ürettiği failure risk içindeki "avoid" ve negatif fiilleri temizle
        String positiveEnforcement = sanitizeNegativeParadox(dynamics.primaryFocalFailureRisk());

        String clause = String.format(
                " Physical contact is structurally locked at %s, %s, explicitly enforcing %s.",
                dynamics.contactPointCoordinate(),
                dynamics.mutualTensionVector(),
                positiveEnforcement
        );
        return trimmed + clause;
    }

    /**
     * İYİLEŞTİRME 3: Negatif Paradoks Dönüştürücü (Syntax Sanitizer)
     * "avoid X" ifadesini yakalayıp modeli pozitif fiziksel zorlamaya iter.
     */
    private static String sanitizeNegativeParadox(String input) {
        if (input == null || input.isBlank()) return "";

        String result = input;
        result = NEGATIVE_AVOID_PATTERN.matcher(result).replaceAll("actively suppressing $1 through explicit mechanical force;");
        result = NEGATIVE_WITHOUT_PATTERN.matcher(result).replaceAll("maintaining unbroken contact against $1;");
        result = NEGATIVE_DO_NOT_PATTERN.matcher(result).replaceAll("countering $1 with rigid physical displacement;");

        // "avoid superficial hand placement" gibi spesifik güreş/kavga klişelerini kökten ezer:
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
            String isolated = sanitizeBannedTokens(pass.isolatedPrompt().trim());
            isolated = sanitizeNegativeParadox(isolated);
            sb.append(String.format("""
                    [PASS %d] Zone: %s
                      Bounding   : %s
                      Prompt     : %s
                    ---------------------------------------------------------------------------
                    """,
                    index,
                    pass.targetZone(),
                    pass.boundingDescription(),
                    isolated
            ));
            index++;
        }

        return sb.toString().stripTrailing();
    }

    public static String generateStructuralReport(SceneContract c) {
        String stanceReport;
        if (c.subjectStance() == null) {
            stanceReport = "  - Subject Stance  : [None - Subjectless Scene / Inanimate Environment]\n";
        } else {
            stanceReport = String.format("""
                  - Stance & Vector : %s | %s
                  - Center of Mass  : %s
                """,
                    c.subjectStance().facingVector(),
                    c.subjectStance().poseDynamics(),
                    c.subjectStance().centerOfGravity()
            );
        }

        String kineticReport;
        if (c.kineticAnchors() == null) {
            kineticReport = "  - Kinetic Anchors : [None - Static Scene]\n";
        } else {
            kineticReport = String.format("""
                  - Physical Origin : %s
                  - Kinetic Vector  : %s
                  - Impact Area     : %s
                """,
                    c.kineticAnchors().exactOriginPoint(),
                    c.kineticAnchors().forceTrajectory(),
                    c.kineticAnchors().physicalImpactArea()
            );
        }

        String phase4;
        if (c.interactionDynamics() == null && (c.regionalPasses() == null || c.regionalPasses().isEmpty())) {
            phase4 = "";
        } else {
            StringBuilder p4 = new StringBuilder();
            p4.append("\n[PHASE 4: INTERACTION DYNAMICS & REGIONAL MASKS]\n");

            if (c.interactionDynamics() != null) {
                p4.append(String.format("""
                          - Contact Point   : %s
                          - Tension Vector  : %s
                          - Enforced Lock   : %s
                        """,
                        c.interactionDynamics().contactPointCoordinate(),
                        c.interactionDynamics().mutualTensionVector(),
                        sanitizeNegativeParadox(c.interactionDynamics().primaryFocalFailureRisk())
                ));
            } else {
                p4.append("  - Interaction Dynamics : [None]\n");
            }

            if (c.regionalPasses() != null && !c.regionalPasses().isEmpty()) {
                p4.append("  - Regional Passes :\n");
                int idx = 1;
                for (SceneContract.RegionalPass pass : c.regionalPasses()) {
                    p4.append(String.format(
                            "      [%d] %s -> %s%n          Prompt: %s%n",
                            idx, pass.targetZone(), pass.boundingDescription(), sanitizeNegativeParadox(pass.isolatedPrompt())
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
                %s  - Primary Light   : %s
                  - Edge Rim Light  : %s
                  - Shutter & Atmos : %s | %s
                %s""",
                c.dramaticAction(),
                c.cameraRig().viewportAngle(), c.cameraRig().focalLength(), c.cameraRig().cameraDistance(),
                c.cameraRig().primarySubjectOffset(),
                stanceReport,
                kineticReport,
                c.environmentalOptics().primaryLightSource(),
                c.environmentalOptics().rimLight(),
                c.environmentalOptics().shutterSpeed(), c.environmentalOptics().atmosphericParticulates(),
                phase4
        );
    }

    private static String sanitizeBannedTokens(String input) {
        String sanitized = input;
        for (Pattern pattern : BANNED_PATTERNS) {
            sanitized = pattern.matcher(sanitized).replaceAll("").replaceAll("\\s{2,}", " ");
        }
        return sanitized.trim();
    }
}