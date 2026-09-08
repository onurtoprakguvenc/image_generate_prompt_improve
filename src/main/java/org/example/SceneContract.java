package org.example;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.fasterxml.jackson.annotation.Nulls;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Universal Data Transfer Object and OpenAPI 3.0 Schema Generator
 * supporting subjectless architectural scenes, optional kinetics, multi-subject
 * physical interaction mapping, regional/inpaint sub-prompts, dynamic prose synthesis,
 * and surreal gravity decoupling.
 *
 * Schema language enforces Positive Geometric Saturation: spectator-safe eye-level
 * camera placement, closed-loop anatomical continuity, and positive spatial exclusion
 * in place of negative disclaimers.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SceneContract(
        @JsonProperty(required = true, value = "dramaticAction")
        String dramaticAction,

        @JsonProperty(required = true, value = "cameraRig")
        CameraRig cameraRig,

        @JsonProperty(value = "subjectStance")
        SubjectStance subjectStance,

        @JsonProperty(value = "kineticAnchors")
        KineticAnchors kineticAnchors,

        @JsonProperty(value = "interactionDynamics")
        InteractionDynamics interactionDynamics,

        @JsonProperty(value = "surrealMountings")
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<SurrealMounting> surrealMountings,

        @JsonProperty(value = "regionalPasses")
        @JsonSetter(nulls = Nulls.AS_EMPTY)
        List<RegionalPass> regionalPasses,

        @JsonProperty(required = true, value = "environmentalOptics")
        EnvironmentalOptics environmentalOptics,

        @JsonProperty(required = true, value = "midjourneyPrompt")
        String midjourneyPrompt,

        @JsonProperty(required = true, value = "fluxPrompt")
        String fluxPrompt
) {

    public enum FrameOffset {
        @JsonProperty("LEFT_THIRD") LEFT_THIRD,
        @JsonProperty("RIGHT_THIRD") RIGHT_THIRD,
        @JsonProperty("CENTER_WEIGHTED") CENTER_WEIGHTED
    }

    public enum RegionalZone {
        @JsonProperty("PRIMARY_SUBJECT") PRIMARY_SUBJECT,
        @JsonProperty("SECONDARY_ACTOR") SECONDARY_ACTOR,
        @JsonProperty("CONTACT_INTERACTION_ZONE") CONTACT_INTERACTION_ZONE,
        @JsonProperty("ENVIRONMENT_BACKGROUND") ENVIRONMENT_BACKGROUND
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CameraRig(
            @JsonProperty(required = true, value = "viewportAngle")
            String viewportAngle,

            @JsonProperty(required = true, value = "focalLength")
            String focalLength,

            @JsonProperty(required = true, value = "primarySubjectOffset")
            FrameOffset primarySubjectOffset,

            @JsonProperty(required = true, value = "cameraDistance")
            String cameraDistance
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SubjectStance(
            @JsonProperty(required = true, value = "facingVector")
            String facingVector,

            @JsonProperty(required = true, value = "poseDynamics")
            String poseDynamics,

            @JsonProperty(required = true, value = "centerOfGravity")
            String centerOfGravity
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record KineticAnchors(
            @JsonProperty(required = true, value = "exactOriginPoint")
            String exactOriginPoint,

            @JsonProperty(required = true, value = "forceTrajectory")
            String forceTrajectory,

            @JsonProperty(required = true, value = "physicalImpactArea")
            String physicalImpactArea
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record InteractionDynamics(
            @JsonProperty(required = true, value = "contactPointCoordinate")
            String contactPointCoordinate,

            @JsonProperty(required = true, value = "mutualTensionVector")
            String mutualTensionVector,

            @JsonProperty(required = true, value = "primaryFocalFailureRisk")
            String primaryFocalFailureRisk
    ) {}

    /**
     * Programmatically breaks down standard furniture/object nouns into raw geometric primitives
     * and surface-mounting verbs to prevent diffusion heuristic shortcuts. Synthesizes engine-specific
     * negative parameters (e.g. converting 'bed on ceiling' to a structural horizontal mattress),
     * strictly limited to verified scene-specific items — never generic anatomical catch-alls.
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record SurrealMounting(
            @JsonProperty(required = true, value = "originalNoun")
            String originalNoun,

            @JsonProperty(required = true, value = "geometricPrimitive")
            String geometricPrimitive,

            @JsonProperty(required = true, value = "surfaceMountingVerb")
            String surfaceMountingVerb,

            @JsonProperty(required = true, value = "requiredNegatives")
            String requiredNegatives
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RegionalPass(
            @JsonProperty(required = true, value = "targetZone")
            RegionalZone targetZone,

            @JsonProperty(required = true, value = "boundingDescription")
            String boundingDescription,

            @JsonProperty(required = true, value = "isolatedPrompt")
            String isolatedPrompt
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record EnvironmentalOptics(
            @JsonProperty(required = true, value = "primaryLightSource")
            String primaryLightSource,

            @JsonProperty(required = true, value = "rimLight")
            String rimLight,

            @JsonProperty(required = true, value = "atmosphericParticulates")
            String atmosphericParticulates,

            @JsonProperty(required = true, value = "shutterSpeed")
            String shutterSpeed
    ) {}

    public static ObjectNode buildGeminiResponseSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        // 1. dramaticAction
        properties.putObject("dramaticAction")
                .put("type", "string")
                .put("description", "Physical, grounded summary of the primary action or environmental atmosphere.");

        // 2. cameraRig — Positive Spectator Vantage enforcement
        ObjectNode cameraNode = properties.putObject("cameraRig");
        cameraNode.put("type", "object");
        ObjectNode camProps = cameraNode.putObject("properties");
        camProps.putObject("viewportAngle")
                .put("type", "string")
                .put("description", "MUST describe a natural standing spectator vantage: virtual camera altitude fixed between 1.6m and 1.8m (average human eye-level), e.g. 'eye-level neutral standing perspective', 'standing medium-shot spectator angle'. Explicitly maintain a positive spatial buffer zone between the lens and the nearest foreground subject — describe the clearance directly (e.g. 'two meters of open clearance to the nearest figure'). Macro or floor-level clipping angles that place the lens at or below ground plane are prohibited; always resolve to the standing vantage instead.");
        camProps.putObject("focalLength").put("type", "string").put("description", "e.g. 24mm wide angle, 50mm normal, 85mm portrait prime. Default to 50mm normal prime for spectator-consistent perspective unless the scene demands otherwise.");
        camProps.putObject("cameraDistance").put("type", "string").put("description", "e.g. intimate close-up, medium-full shot, vast landscape perspective. Must be compatible with a standing eye-level vantage and preserve foreground clearance.");

        ArrayNode offsetEnum = camProps.putObject("primarySubjectOffset")
                .put("type", "string")
                .put("description", "Horizontal placement of the focal center. Prefer LEFT_THIRD or RIGHT_THIRD as default tension axes. Permit CENTER_WEIGHTED strictly for intentional axial architectural symmetry, one-point perspective, or formal direct-stare portraits.")
                .putArray("enum");
        offsetEnum.add("LEFT_THIRD").add("RIGHT_THIRD").add("CENTER_WEIGHTED");

        ArrayNode camReq = cameraNode.putArray("required");
        camReq.add("viewportAngle").add("focalLength").add("primarySubjectOffset").add("cameraDistance");

        // 3. subjectStance (Optional) — Closed-loop anatomical chain enforcement
        ObjectNode stanceNode = properties.putObject("subjectStance");
        stanceNode.put("type", "object");
        stanceNode.put("description", "Character pose parameters. MUST BE OMITTED or set to null if the scene is an inanimate space, vehicle, architecture, or landscape. When present, every described pose MUST resolve to a complete, closed-loop anatomical chain: head-to-toe intact figure, with hands and wrists structurally attached to fully rendered forearms, shoulders, and torso at every point in the described posture — including inverted or unconventional orientations.");
        ObjectNode stanceProps = stanceNode.putObject("properties");
        stanceProps.putObject("facingVector").put("type", "string").put("description", "e.g. three-quarter profile facing left, direct gaze into lens");
        stanceProps.putObject("poseDynamics").put("type", "string").put("description", "e.g. relaxed seated posture, coiled defensive crouch. Must describe the full kinematic chain as continuous and intact, never an isolated limb or torso fragment.");
        stanceProps.putObject("centerOfGravity").put("type", "string").put("description", "e.g. stable grounded center of mass, weight on rear heel");
        ArrayNode stanceReq = stanceNode.putArray("required");
        stanceReq.add("facingVector").add("poseDynamics").add("centerOfGravity");

        // 4. kineticAnchors (Optional)
        ObjectNode kineticNode = properties.putObject("kineticAnchors");
        kineticNode.put("type", "object");
        kineticNode.put("description", "Active force parameters. MUST BE OMITTED or set to null unless an active physical dynamic force, energy discharge, or projectile is active.");
        ObjectNode kineticProps = kineticNode.putObject("properties");
        kineticProps.putObject("exactOriginPoint").put("type", "string").put("description", "Point of physical force origin");
        kineticProps.putObject("forceTrajectory").put("type", "string").put("description", "Directional vector of force");
        kineticProps.putObject("physicalImpactArea").put("type", "string").put("description", "Zone of collision or displacement");
        ArrayNode kineticReq = kineticNode.putArray("required");
        kineticReq.add("exactOriginPoint").add("forceTrajectory").add("physicalImpactArea");

        // 5. interactionDynamics (Optional) — Closed-loop anatomical chain enforcement
        ObjectNode interactionNode = properties.putObject("interactionDynamics");
        interactionNode.put("type", "object");
        interactionNode.put("description", "Physical contact mapping between two or more actively interacting subjects (grappling, striking, weapon-on-flesh contact, hand-to-ground load bearing). MUST BE OMITTED or set to null for single-subject or non-contact scenes. Every contact described MUST define unbroken kinematic linkage — e.g. 'wrists and hands structurally attached to fully rendered forearms, shoulders, and inverted torso' — so the model is never given license to truncate or float a limb. Exists to suppress diffusion models' tendency to resolve close contact into non-contact clichés (crossed blades, symmetric fist-bumps, dormant limbs) by fully saturating the contact geometry instead of describing what to avoid. Explicitly separate primary subjects from the surrounding environment and spectators to prevent multi-actor hybrid mutations.");
        ObjectNode interactionProps = interactionNode.putObject("properties");
        interactionProps.putObject("contactPointCoordinate").put("type", "string").put("description", "Textual anchor for where the interaction physically occurs, e.g. 'upper right quadrant, neck level'");
        interactionProps.putObject("mutualTensionVector").put("type", "string").put("description", "Description of kinetic resistance between subjects, e.g. 'opposing inward lateral force', 'anchored absorption against downward momentum'");
        interactionProps.putObject("primaryFocalFailureRisk").put("type", "string").put("description", "Phrase this as a POSITIVE structural commitment, not a prohibition — describe what IS anatomically present and locked in place rather than what to avoid, e.g. 'palms fully planted and bearing weight, wrists continuous into forearms, shoulders squared and load-bearing' rather than 'avoid floating hands'.");
        ArrayNode interactionReq = interactionNode.putArray("required");
        interactionReq.add("contactPointCoordinate").add("mutualTensionVector").add("primaryFocalFailureRisk");

        // 6. surrealMountings (Optional — Lexical Decoupling for genuine non-standard physics)
        ObjectNode surrealNode = properties.putObject("surrealMountings");
        surrealNode.put("type", "array");
        surrealNode.put("description", "Surreal or inverted-gravity object mappings. MUST BE OMITTED or empty unless the scene genuinely contains non-standard physics or anti-gravity mounting (e.g. furniture affixed to a ceiling plane). Breaks down standard furniture/object nouns into geometric primitives and mounting verbs to prevent diffusion heuristic defaults (e.g. a ceiling-mounted mattress defaulting to a bunk-bed render). requiredNegatives must list only genuine, scene-specific items being displaced by the surreal mounting (e.g. 'ladder, support posts, legs, stilts') — never generic anatomical terms.");
        ObjectNode surrealItems = surrealNode.putObject("items");
        surrealItems.put("type", "object");
        ObjectNode surrealItemProps = surrealItems.putObject("properties");
        surrealItemProps.putObject("originalNoun").put("type", "string").put("description", "Original noun, e.g. 'bed frame on ceiling'");
        surrealItemProps.putObject("geometricPrimitive").put("type", "string").put("description", "Deconstructed geometry, e.g. 'flush horizontal mattress with zero clearance'");
        surrealItemProps.putObject("surfaceMountingVerb").put("type", "string").put("description", "Mounting verb, e.g. 'suctioned flush against the plaster ceiling plane'");
        surrealItemProps.putObject("requiredNegatives").put("type", "string").put("description", "Only genuine, scene-specific displaced components, e.g. 'ladder, bunk bed, support posts, legs, stilts'. Never generic anatomical catch-alls.");
        ArrayNode surrealReq = surrealItems.putArray("required");
        surrealReq.add("originalNoun").add("geometricPrimitive").add("surfaceMountingVerb").add("requiredNegatives");

        // 7. regionalPasses (Optional)
        ObjectNode regionalArrayNode = properties.putObject("regionalPasses");
        regionalArrayNode.put("type", "array");
        regionalArrayNode.put("description", "Targeted regional/inpainting staging passes. MUST BE OMITTED or empty for single-subject or landscape-only scenes. When interactionDynamics is present, populate at least 2 distinct passes (e.g. a base anchor pass and a local contact-point pass) so isolated inpainting prompts are immediately available.");
        ObjectNode regionalItems = regionalArrayNode.putObject("items");
        regionalItems.put("type", "object");
        ObjectNode regionalItemProps = regionalItems.putObject("properties");

        ArrayNode zoneEnum = regionalItemProps.putObject("targetZone")
                .put("type", "string")
                .put("description", "Identifier for which staged element this regional pass targets.")
                .putArray("enum");
        zoneEnum.add("PRIMARY_SUBJECT").add("SECONDARY_ACTOR").add("CONTACT_INTERACTION_ZONE").add("ENVIRONMENT_BACKGROUND");

        regionalItemProps.putObject("boundingDescription").put("type", "string").put("description", "Spatial bounding hint for this region, e.g. 'Right third, neck and hand contact coordinates'");
        regionalItemProps.putObject("isolatedPrompt").put("type", "string").put("description", "A targeted, hyper-specific prompt for inpainting or regional prompting tools, describing only local geometry and physical continuity within this zone, phrased positively, with zero scene-wide fluff.");

        ArrayNode regionalItemReq = regionalItems.putArray("required");
        regionalItemReq.add("targetZone").add("boundingDescription").add("isolatedPrompt");

        // 8. environmentalOptics
        ObjectNode opticsNode = properties.putObject("environmentalOptics");
        opticsNode.put("type", "object");
        ObjectNode opticsProps = opticsNode.putObject("properties");
        opticsProps.putObject("primaryLightSource").put("type", "string").put("description", "e.g. soft diffuse morning window spill, overhead fluorescent fixtures");
        opticsProps.putObject("rimLight").put("type", "string").put("description", "e.g. subtle warm edge backlight, razor-sharp cool specular rim");
        opticsProps.putObject("atmosphericParticulates").put("type", "string").put("description", "e.g. floating dust motes in sunbeam, clean indoor air, fog");
        opticsProps.putObject("shutterSpeed").put("type", "string").put("description", "e.g. 1/125s gentle natural exposure, 1/2000s freeze-action shutter");
        ArrayNode opticsReq = opticsNode.putArray("required");
        opticsReq.add("primaryLightSource").add("rimLight").add("atmosphericParticulates").add("shutterSpeed");

        // 9. midjourneyPrompt — Positive Geometric Saturation
        properties.putObject("midjourneyPrompt")
                .put("type", "string")
                .put("description", "A fully synthesized, organic descriptive prose paragraph written in natural cinematic English adhering strictly to all extracted optics, spatial asymmetry, physics, and (when present) interactionDynamics/surrealMountings constraints. DO NOT append flags here; flags are managed dynamically. Prioritize POSITIVE spatial exclusion over negative disclaimers: rather than saying what to avoid, fully occupy and describe the coordinate space so the excluded outcome has no room to occur. Describe physical displacement concretely (skin indentation, fabric bunching, knuckle whitening) rather than abstract intent. When a subject is inverted or in an unconventional pose (e.g. walking on hands), positively designate every extremity's position explicitly — e.g. 'both feet and shoes are fully rendered and elevated vertically into the open sky above, with palms serving as the sole physical anchor point against the ground plane' — rather than instructing what to omit. Mandate that synthesized prompts explicitly separate primary subjects from the surrounding environment/spectators to prevent multi-actor hybrid mutations.");

        // 10. fluxPrompt — Positive Geometric Saturation
        properties.putObject("fluxPrompt")
                .put("type", "string")
                .put("description", "A comprehensive, tactile natural language prose paragraph optimized for Flux text-following. Written with fluid sentence variety, zero boilerplate, and zero Midjourney syntax flags. Honor the same positive-saturation constraints as midjourneyPrompt: state what is fully present and anatomically continuous rather than what is missing or forbidden. When interactionDynamics or surrealMountings are present, honor the same contact/geometry constraints as midjourneyPrompt. Mandate that synthesized prompts explicitly separate primary subjects from the surrounding environment/spectators to prevent multi-actor hybrid mutations.");

        // Root required properties
        ArrayNode rootRequired = schema.putArray("required");
        rootRequired.add("dramaticAction")
                .add("cameraRig")
                .add("environmentalOptics")
                .add("midjourneyPrompt")
                .add("fluxPrompt");

        return schema;
    }
}