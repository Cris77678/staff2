package me.drex.staffmod.mixin;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.util.JailManager;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class MovementMixin {

    @Shadow
    public ServerPlayer player;

    private int jailCheckTick = 0;
    private int freezeNotifyTick = 0;

    @Inject(method = "handleMovePlayer", at = @At("HEAD"), cancellable = true)
    private void staffmod$handleMovement(ServerboundMovePlayerPacket packet, CallbackInfo ci) {
        PlayerData pd = DataStore.get(player.getUUID());
        if (pd == null) return;

        // FREEZE: cancelar movimiento y teletransportar al último pos segura
        if (pd.frozen) {
            // Teleportar a posición actual del servidor para rechazar el paquete
            player.connection.teleport(
                player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot());
            ci.cancel();

            // Notificación cada ~3 segundos (60 ticks)
            if (++freezeNotifyTick >= 60) {
                freezeNotifyTick = 0;
                player.sendSystemMessage(Component.literal(
                    "§b[sᴛᴀꜰꜰ] Estás congelado. No puedes moverte hasta que el staff te libere."));
            }
            return;
        }

        // JAIL bounds check cada ~1 segundo (20 ticks)
        if (pd.isJailActive()) {
            if (++jailCheckTick >= 20) {
                jailCheckTick = 0;
                JailManager.checkJailBounds(player);
            }
        }
    }
}
