package me.drex.staffmod.mixin;

import me.drex.staffmod.features.VanishManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FIX: Los mobs usan TargetGoal para encontrar objetivos.
 * Al apuntar a la clase base TargetGoal, cubrimos TODAS las IA de ataque.
 */
@Mixin(TargetGoal.class)
public abstract class VanishMobTargetMixin {

    // Al usar solo "canAttack", evitamos problemas de refmap con los parámetros exactos
    @Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
    private void staffmod$ignoreVanishedPlayers(
        LivingEntity target,
        net.minecraft.world.entity.ai.targeting.TargetingConditions conditions,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (target instanceof Player player) {
            if (VanishManager.isVanished(player.getUUID())) {
                cir.setReturnValue(false); // Finge que el jugador no es atacable
            }
        }
    }
}
