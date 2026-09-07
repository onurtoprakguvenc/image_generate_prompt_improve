package org.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.List;

/**
 * Universal Data Transfer Object and OpenAPI 3.0 Schema Generator
 * supporting subjectless architectural scenes, optional kinetics, multi-subject
 * physical interaction mapping, regional/inpaint sub-prompts, and dynamic prose synthesis.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record SceneContract(
        @JsonProperty(required = true, value = "dramaticAction")
        String dramaticAction,

        @JsonProperty(required = true, value = "cameraRig")
        CameraRig cameraRig,

        @JsonProperty(value = "subjectStance")
        SubjectStance subjectStance, // Nullable: omitted in subjectless / environmental scenes

        @JsonProperty(value = "kineticAnchors")
        KineticAnchors kineticAnchors, // Nullable: omitted in dormant / static scenes

        @JsonProperty(value = "interactionDynamics")
        InteractionDynamics interactionDynamics, // Nullable: omitted unless 2+ subjects physically interact

        @JsonProperty(value = "regionalPasses")
        List<RegionalPass> regionalPasses, // Nullable/empty: omitted for single-subject or landscape-only scenes

        @JsonProperty(required = true, value = "environmentalOptics")
        EnvironmentalOptics environmentalOptics,

        @JsonProperty(required = true, value = "midjourneyPrompt")
        String midjourneyPrompt, // Synthesized organic prose for Midjourney

        @JsonProperty(required = true, value = "fluxPrompt")
        String fluxPrompt // Synthesized descriptive prose for Flux
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

    public record SubjectStance(
            @JsonProperty(required = true, value = "facingVector")
            String facingVector,

            @JsonProperty(required = true, value = "poseDynamics")
            String poseDynamics,

            @JsonProperty(required = true, value = "centerOfGravity")
            String centerOfGravity
    ) {}

    public record KineticAnchors(
            @JsonProperty(required = true, value = "exactOriginPoint")
            String exactOriginPoint,

            @JsonProperty(required = true, value = "forceTrajectory")
            String forceTrajectory,

            @JsonProperty(required = true, value = "physicalImpactArea")
            String physicalImpactArea
    ) {}

    /**
     * Physical contact/interaction mapping for scenes with 2+ actively interacting subjects.
     * Exists specifically to counter diffusion models' tendency to resolve close-contact
     * physical interactions into familiar, non-contact tropes (crossed blades, symmetric
     * fist-bumps, dormant limbs) rather than true geometric contact.
     */
    public record InteractionDynamics(
            @JsonProperty(required = true, value = "contactPointCoordinate")
            String contactPointCoordinate, // e.g. "upper right quadrant, neck level"

            @JsonProperty(required = true, value = "mutualTensionVector")
            String mutualTensionVector, // e.g. "opposing inward lateral force"

            @JsonProperty(required = true, value = "primaryFocalFailureRisk")
            String primaryFocalFailureRisk // the specific cliché to actively suppress
    ) {}

    /**
     * A targeted, hyper-specific staging pass intended for regional prompting or inpainting
     * tools (Midjourney Vary Region/Inpaint, Flux Regional LoRA). Describes only local
     * geometry and physical friction for a bounded zone, without scene-wide fluff.
     */
    public record RegionalPass(
            @JsonProperty(required = true, value = "targetZone")
            RegionalZone targetZone,

            @JsonProperty(required = true, value = "boundingDescription")
            String boundingDescription,

            @JsonProperty(required = true, value = "isolatedPrompt")
            String isolatedPrompt
    ) {}

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

    /**
     * Constructs the OpenAPI 3.0 JSON schema for Gemini's generationConfig.response_schema.
     */
    public static ObjectNode buildGeminiResponseSchema(ObjectMapper mapper) {
        ObjectNode schema = mapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = schema.putObject("properties");

        // 1. dramaticAction (Required)
        properties.putObject("dramaticAction")
                .put("type", "string")
                .put("description", "Physical, grounded summary of the primary action or environmental atmosphere.");

        // 2. cameraRig (Required)
        ObjectNode cameraNode = properties.putObject("cameraRig");
        cameraNode.put("type", "object");
        ObjectNode camProps = cameraNode.putObject("properties");
        camProps.putObject("viewportAngle").put("type", "string").put("description", "e.g. ground-level worm's-eye 45 deg, eye-level neutral, high overhead");
        camProps.putObject("focalLength").put("type", "string").put("description", "e.g. 24mm wide angle, 50mm normal, 85mm portrait prime");
        camProps.putObject("cameraDistance").put("type", "string").put("description", "e.g. intimate close-up, medium-full shot, vast landscape perspective");

        ArrayNode offsetEnum = camProps.putObject("primarySubjectOffset")
                .put("type", "string")
                .put("description", "Horizontal placement of the focal center. Prefer LEFT_THIRD or RIGHT_THIRD as default tension axes. Permit CENTER_WEIGHTED strictly for intentional axial architectural symmetry, one-point perspective, or formal direct-stare portraits.")
                .putArray("enum");
        offsetEnum.add("LEFT_THIRD").add("RIGHT_THIRD").add("CENTER_WEIGHTED");

        ArrayNode camReq = cameraNode.putArray("required");
        camReq.add("viewportAngle").add("focalLength").add("primarySubjectOffset").add("cameraDistance");

        // 3. subjectStance (OPTIONAL: Omit if the scene is an inanimate landscape, architecture, or object)
        ObjectNode stanceNode = properties.putObject("subjectStance");
        stanceNode.put("type", "object");
        stanceNode.put("description", "Character pose parameters. MUST BE OMITTED or set to null if the scene is an inanimate space, vehicle, architecture, or landscape.");
        ObjectNode stanceProps = stanceNode.putObject("properties");
        stanceProps.putObject("facingVector").put("type", "string").put("description", "e.g. three-quarter profile facing left, direct gaze into lens");
        stanceProps.putObject("poseDynamics").put("type", "string").put("description", "e.g. relaxed seated posture, coiled defensive crouch");
        stanceProps.putObject("centerOfGravity").put("type", "string").put("description", "e.g. stable grounded center of mass, weight on rear heel");
        ArrayNode stanceReq = stanceNode.putArray("required");
        stanceReq.add("facingVector").add("poseDynamics").add("centerOfGravity");

        // 4. kineticAnchors (OPTIONAL: Omit in dormant or static scenes)
        ObjectNode kineticNode = properties.putObject("kineticAnchors");
        kineticNode.put("type", "object");
        kineticNode.put("description", "Active force parameters. MUST BE OMITTED or set to null unless an active physical dynamic force, energy discharge, or projectile is active.");
        ObjectNode kineticProps = kineticNode.putObject("properties");
        kineticProps.putObject("exactOriginPoint").put("type", "string").put("description", "Point of physical force origin");
        kineticProps.putObject("forceTrajectory").put("type", "string").put("description", "Directional vector of force");
        kineticProps.putObject("physicalImpactArea").put("type", "string").put("description", "Zone of collision or displacement");
        ArrayNode kineticReq = kineticNode.putArray("required");
        kineticReq.add("exactOriginPoint").add("forceTrajectory").add("physicalImpactArea");

        // 5. interactionDynamics (OPTIONAL: Omit unless 2+ subjects are in direct physical contact)
        ObjectNode interactionNode = properties.putObject("interactionDynamics");
        interactionNode.put("type", "object");
        interactionNode.put("description", "Physical contact mapping between two or more actively interacting subjects (grappling, striking, weapon-on-flesh contact). MUST BE OMITTED or set to null for single-subject or non-contact scenes. Exists to suppress diffusion models' tendency to resolve close contact into non-contact clichés (crossed blades, symmetric fist-bumps, dormant limbs).");
        ObjectNode interactionProps = interactionNode.putObject("properties");
        interactionProps.putObject("contactPointCoordinate").put("type", "string").put("description", "Textual anchor for where the interaction physically occurs, e.g. 'upper right quadrant, neck level'");
        interactionProps.putObject("mutualTensionVector").put("type", "string").put("description", "Description of kinetic resistance between subjects, e.g. 'opposing inward lateral force', 'anchored absorption against downward momentum'");
        interactionProps.putObject("primaryFocalFailureRisk").put("type", "string").put("description", "The specific cliché to actively suppress, e.g. 'avoid blades crossing in mid-air; blade edge must indent skin surface', 'avoid attackers punching each other instead of the target'");
        ArrayNode interactionReq = interactionNode.putArray("required");
        interactionReq.add("contactPointCoordinate").add("mutualTensionVector").add("primaryFocalFailureRisk");

        // 6. regionalPasses (OPTIONAL: Omit for single-subject or landscape-only scenes)
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
        regionalItemProps.putObject("isolatedPrompt").put("type", "string").put("description", "A targeted, hyper-specific prompt for inpainting or regional prompting tools, describing only local geometry and physical friction within this zone, with zero scene-wide fluff.");

        ArrayNode regionalItemReq = regionalItems.putArray("required");
        regionalItemReq.add("targetZone").add("boundingDescription").add("isolatedPrompt");

        // 7. environmentalOptics (Required)
        ObjectNode opticsNode = properties.putObject("environmentalOptics");
        opticsNode.put("type", "object");
        ObjectNode opticsProps = opticsNode.putObject("properties");
        opticsProps.putObject("primaryLightSource").put("type", "string").put("description", "e.g. soft diffuse morning window spill, overhead fluorescent fixtures");
        opticsProps.putObject("rimLight").put("type", "string").put("description", "e.g. subtle warm edge backlight, razor-sharp cool specular rim");
        opticsProps.putObject("atmosphericParticulates").put("type", "string").put("description", "e.g. floating dust motes in sunbeam, clean indoor air, fog");
        opticsProps.putObject("shutterSpeed").put("type", "string").put("description", "e.g. 1/125s gentle natural exposure, 1/2000s freeze-action shutter");
        ArrayNode opticsReq = opticsNode.putArray("required");
        opticsReq.add("primaryLightSource").add("rimLight").add("atmosphericParticulates").add("shutterSpeed");

        // 8. midjourneyPrompt (Required)
        properties.putObject("midjourneyPrompt")
                .put("type", "string")
                .put("description", "A fully synthesized, organic descriptive prose paragraph written in natural cinematic English adhering strictly to all extracted optics, spatial asymmetry, physics, and (when present) interactionDynamics contact/failure-risk constraints. DO NOT append flags here; flags are managed dynamically. When interactionDynamics is present, describe physical displacement (skin indentation, fabric bunching, knuckle whitening) rather than abstract intent.");

        // 9. fluxPrompt (Required)
        properties.putObject("fluxPrompt")
                .put("type", "string")
                .put("description", "A comprehensive, tactile natural language prose paragraph optimized for Flux text-following. Written with fluid sentence variety, zero boilerplate, and zero Midjourney syntax flags. When interactionDynamics is present, honor the same contact/failure-risk constraints as midjourneyPrompt.");

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