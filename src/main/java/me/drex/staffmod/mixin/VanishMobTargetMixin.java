package me.drex.staffmod.mixin;

import me.drex.staffmod.features.VanishManager;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * FIX: Los mobs usan NearestAttackableTargetGoal para encontrar objetivos.
 * setInvisible(true) reduce el rango de detección pero NO lo elimina —
 * a corta distancia los mobs siguen viendo y atacando al jugador.
 *
 * Este mixin inyecta en el método canAttack() de NearestAttackableTargetGoal
 * para devolver false cuando el objetivo es un jugador con vanish activo,
 * haciendo que los mobs lo ignoren completamente como si no existiera.
 */
@Mixin(NearestAttackableTargetGoal.class)
public abstract class VanishMobTargetMixin {

    @Inject(method = "canAttack(Lnet/minecraft/world/entity/LivingEntity;Lnet/minecraft/world/entity/ai/targeting/TargetingConditions;)Z",
            at = @At("HEAD"), cancellable = true)
    private void staffmod$ignoreVanishedPlayers(
        LivingEntity target,
        net.minecraft.world.entity.ai.targeting.TargetingConditions conditions,
        CallbackInfoReturnable<Boolean> cir
    ) {
        if (target instanceof Player player) {
            if (VanishManager.isVanished(player.getUUID())) {
                cir.setReturnValue(false);
            }
        }
    }
}
