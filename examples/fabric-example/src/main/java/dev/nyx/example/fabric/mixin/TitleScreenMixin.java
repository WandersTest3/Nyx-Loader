package dev.nyx.example.fabric.mixin;

import net.minecraft.class_1600;
import net.minecraft.class_388;
import net.minecraft.class_624;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(class_624.class)
public abstract class TitleScreenMixin extends class_388 {

    @Inject(method = "method_1025", at = @At("TAIL"))
    private void nyxExample$drawLabel(int mouseX, int mouseY, float partialTicks, CallbackInfo info) {
        String text = "Fabric via Nyx | FPS " + class_1600.field_3787;
        this.field_1234.method_956(text, 2, this.field_1231 - 20, 0xFFAA77DD);
    }
}
