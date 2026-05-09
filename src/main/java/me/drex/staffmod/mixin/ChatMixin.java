package me.drex.staffmod.mixin;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.gui.ActionExecutor;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ChatMixin {

    @Shadow
    public ServerPlayer player;

    // Interceptamos el paquete RAW del chat antes de que el servidor lo procese
    @Inject(method = "handleChat", at = @At("HEAD"), cancellable = true)
    private void staffmod$blockMutedChat(ServerboundChatPacket packet, CallbackInfo ci) {
        PlayerData pd = DataStore.get(player.getUUID());
        if (pd == null) return;

        // 1. Bloqueo si está Muteado
        if (pd.isMuteActive()) {
            player.sendSystemMessage(Component.literal(
                "§c[sᴛᴀꜰꜰ] Estás silenciado. No puedes hablar. Expira: §e"
                + PlayerData.formatExpiry(pd.muteExpiry)));
            ci.cancel(); // Esto destruye el paquete, nadie lo verá
            return;
        }

        // 2. Bloqueo si está en la Cárcel (Jail)
        if (pd.isJailActive()) {
            player.sendSystemMessage(Component.literal(
                "§c[sᴛᴀꜰꜰ] No puedes hablar mientras estés en la cárcel."));
            ci.cancel();
            return;
        }

        // 3. Redirigir al Staff Chat si el Toggle está activo
        if (DataStore.isStaffChatToggled(player.getUUID()) && PermissionUtil.has(player, "staffmod.use")) {
            ci.cancel(); // Destruimos el mensaje público
            ActionExecutor.sendStaffChatMessage(player, packet.message()); // Lo mandamos en privado
        }
    }
}
