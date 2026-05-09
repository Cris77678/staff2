package me.drex.staffmod.mixin;

import me.drex.staffmod.features.VanishManager;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FIX: Cuando un jugador con vanish activo es teletransportado (cualquier causa:
 * /tp, GUI del staff, jail, unban, etc.), Minecraft reenvía automáticamente
 * ClientboundPlayerInfoUpdatePacket a todos los jugadores y resetea setInvisible(false).
 *
 * Este mixin intercepta el método teleportTo de ServerPlayer y, si el jugador
 * está en vanish, vuelve a aplicar los efectos de vanish tras el teleport.
 *
 * Se usa un flag para evitar recursión en caso de que reapplyVanishAfterTeleport
 * llame internamente a algún método que también dispare teleportTo.
 */
@Mixin(ServerPlayer.class)
public abstract class TeleportVanishMixin {

    // Flag para evitar recursión
    private boolean staffmod$reapplyingVanish = false;

    // ── Intercept teleportTo(ServerLevel, double, double, double, float, float) ──
    @Inject(
        method = "teleportTo(Lnet/minecraft/server/level/ServerLevel;DDDFF)V",
        at = @At("RETURN")
    )
    private void staffmod$onTeleportTo(
        ServerLevel level, double x, double y, double z, float yaw, float pitch,
        CallbackInfo ci
    ) {
        if (staffmod$reapplyingVanish) return;
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (!VanishManager.isVanished(self.getUUID())) return;

        // Programar re-aplicación al siguiente tick del servidor para que el TP haya terminado
        staffmod$reapplyingVanish = true;
        self.getServer().execute(() -> {
            try {
                VanishManager.reapplyVanishAfterTeleport(self);
            } finally {
                staffmod$reapplyingVanish = false;
            }
        });
    }
}
