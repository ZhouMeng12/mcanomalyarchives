package net.mcreator.mcanomalyarchives.mixin;

import java.io.InputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import com.mojang.blaze3d.shaders.Program;
import com.mojang.blaze3d.preprocessor.GlslPreprocessor;
import net.mcreator.mcanomalyarchives.client.earth.EarthShaderSource;
import org.apache.commons.io.IOUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = Program.class, remap = false)
public abstract class EarthProgramMixin {
    @Redirect(method = "compileShaderInternal", at = @At(value = "INVOKE", target = "Lorg/apache/commons/io/IOUtils;toString(Ljava/io/InputStream;Ljava/nio/charset/Charset;)Ljava/lang/String;"))
    private static String earth$source(InputStream input, Charset charset, Program.Type type, String name,
                                      InputStream original, String pack, GlslPreprocessor processor) throws IOException {
        return EarthShaderSource.patch(type == Program.Type.VERTEX, name, IOUtils.toString(input, charset));
    }
}
