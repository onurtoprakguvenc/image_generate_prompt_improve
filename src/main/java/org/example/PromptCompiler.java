package org.example;

import java.util.List;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Intelligent prompt compiler and parameter manager.
 * Eradicates rigid Mad-Libs concatenation in favor of organic model prose
 * while enforcing dynamic syntax flags and buzzword sanitization.
 */
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

    private static final Pattern PARAMETER_CLEANUP = Pattern.compile("(--ar|--style|--v|--chaos|--weird|--stylize).*$", Pattern.CASE_INSENSITIVE);
    private static final Pattern PUNCTUATION_CLEANUP = Pattern.compile("[-–—\\s,;]+$");

    /**
     * Compiles the final prompt applying dynamic engine flags.
     */
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

        // Strip any residual flags generated in the prose string
        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.endsWith(".")) {
            base += ".";
        }

        String sanitized = sanitizeBannedTokens(base);
        String cleanFlags = (flags != null && !flags.isBlank()) ? flags.trim() : "--ar 16:9 --style raw --v 6.1";

        return sanitized + " " + cleanFlags;
    }

    private static String compileFlux(SceneContract c) {
        String prose = (c.fluxPrompt() != null && !c.fluxPrompt().isBlank())
                ? c.fluxPrompt()
                : c.dramaticAction();

        // Strip any accidental flags
        String base = PARAMETER_CLEANUP.matcher(prose.trim()).replaceAll("").trim();
        base = PUNCTUATION_CLEANUP.matcher(base).replaceAll("").trim();
        if (!base.endsWith(".")) {
            base += ".";
        }

        return sanitizeBannedTokens(base);
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
                """,
                c.dramaticAction(),
                c.cameraRig().viewportAngle(), c.cameraRig().focalLength(), c.cameraRig().cameraDistance(),
                c.cameraRig().primarySubjectOffset(),
                stanceReport,
                kineticReport,
                c.environmentalOptics().primaryLightSource(),
                c.environmentalOptics().rimLight(),
                c.environmentalOptics().shutterSpeed(), c.environmentalOptics().atmosphericParticulates()
        );
    }

    private static String sanitizeBannedTokens(String input) {
        String sanitized = input;
        for (String banned : BANNED_TOKENS) {
            sanitized = Pattern.compile("\\b" + Pattern.quote(banned) + "\\b", Pattern.CASE_INSENSITIVE)
                    .matcher(sanitized)
                    .replaceAll("")
                    .replaceAll("\\s{2,}", " ");
        }
        return sanitized.trim();
    }
}