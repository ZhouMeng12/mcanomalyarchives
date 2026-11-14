package net.mcreator.mcanomalyarchives.client.earth;

import java.util.Set;
import java.util.regex.Pattern;

/** Narrow source patch: preserves vanilla/resource-pack lighting and fragment programs. */
public final class EarthShaderSource {
    private static final Set<String> TERRAIN = Set.of("rendertype_solid", "rendertype_cutout", "rendertype_cutout_mipped",
        "rendertype_translucent", "rendertype_translucent_moving_block", "rendertype_tripwire");
    private static final Pattern POSITION = Pattern.compile("gl_Position\\s*=\\s*ProjMat\\s*\\*\\s*ModelViewMat\\s*\\*\\s*(vec4\\([^;]+\\))\\s*;");
    private static final String FUNCTION = """
        uniform float McaEarthStrength;
        uniform vec3 McaEarthCenter;
        uniform vec3 McaEarthUp;
        vec4 mcaEarthWarp(vec4 viewPosition) {
            if (McaEarthStrength <= 0.0) return viewPosition;
            vec3 relative = viewPosition.xyz - McaEarthCenter;
            float vertical = dot(relative, McaEarthUp);
            vec3 horizontal = relative - McaEarthUp * vertical;
            float radius = mix(24.0, 128.0, McaEarthStrength);
            float falloff = pow(max(0.0, 1.0 - dot(horizontal,horizontal)/(radius*radius)),2.0);
            falloff *= 1.0-smoothstep(128.0,256.0,abs(vertical));
            float amount = McaEarthStrength * falloff;
            viewPosition.xyz -= horizontal * amount * 0.58;
            viewPosition.xyz -= McaEarthUp * amount * (48.0 + max(0.0,vertical)*0.3);
            return viewPosition;
        }
        """;
    private EarthShaderSource() { }
    public static String patch(boolean vertex, String name, String source) {
        if (!vertex || source.contains("mcaEarthWarp")) return source;
        if (name.startsWith("minecraft:")) name = name.substring("minecraft:".length());
        if (!(TERRAIN.contains(name) || name.startsWith("rendertype_entity_"))) return source;
        var position = POSITION.matcher(source);
        if (!position.find()) return source; // Unrecognised custom shaders are left intact.
        String patched = position.replaceFirst("gl_Position = ProjMat * mcaEarthWarp(ModelViewMat * $1);");
        int main = patched.indexOf("void main()");
        if (main < 0) main = patched.indexOf("void main(");
        if (main < 0) return source;
        return patched.substring(0, main) + FUNCTION + "\n" + patched.substring(main);
    }
}
