package org.example;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/**
 * Universal Data Transfer Object and OpenAPI 3.0 Schema Generator
 * supporting subjectless architectural scenes, optional kinetics, and dynamic prose synthesis.
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

        // 5. environmentalOptics (Required)
        ObjectNode opticsNode = properties.putObject("environmentalOptics");
        opticsNode.put("type", "object");
        ObjectNode opticsProps = opticsNode.putObject("properties");
        opticsProps.putObject("primaryLightSource").put("type", "string").put("description", "e.g. soft diffuse morning window spill, overhead fluorescent fixtures");
        opticsProps.putObject("rimLight").put("type", "string").put("description", "e.g. subtle warm edge backlight, razor-sharp cool specular rim");
        opticsProps.putObject("atmosphericParticulates").put("type", "string").put("description", "e.g. floating dust motes in sunbeam, clean indoor air, fog");
        opticsProps.putObject("shutterSpeed").put("type", "string").put("description", "e.g. 1/125s gentle natural exposure, 1/2000s freeze-action shutter");
        ArrayNode opticsReq = opticsNode.putArray("required");
        opticsReq.add("primaryLightSource").add("rimLight").add("atmosphericParticulates").add("shutterSpeed");

        // 6. midjourneyPrompt (Required)
        properties.putObject("midjourneyPrompt")
                .put("type", "string")
                .put("description", "A fully synthesized, organic descriptive prose paragraph written in natural cinematic English adhering strictly to all extracted optics, spatial asymmetry, and physics. DO NOT append flags here; flags are managed dynamically.");

        // 7. fluxPrompt (Required)
        properties.putObject("fluxPrompt")
                .put("type", "string")
                .put("description", "A comprehensive, tactile natural language prose paragraph optimized for Flux text-following. Written with fluid sentence variety, zero boilerplate, and zero Midjourney syntax flags.");

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