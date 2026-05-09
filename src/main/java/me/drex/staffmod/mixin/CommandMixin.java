package me.drex.staffmod.mixin;

import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundChatCommandPacket;
import net.minecraft.network.protocol.game.ServerboundChatCommandSignedPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CommandMixin {

    @Shadow
    public ServerPlayer player;

    @Inject(method = "handleChatCommand", at = @At("HEAD"), cancellable = true)
    private void staffmod$blockJailedCommands(ServerboundChatCommandPacket packet, CallbackInfo ci) {
        checkRestrictions(packet.command(), ci);
    }

    @Inject(method = "handleSignedChatCommand", at = @At("HEAD"), cancellable = true)
    private void staffmod$blockJailedSignedCommands(ServerboundChatCommandSignedPacket packet, CallbackInfo ci) {
        checkRestrictions(packet.command(), ci);
    }

    private void checkRestrictions(String command, CallbackInfo ci) {
        PlayerData pd = DataStore.get(player.getUUID());
        if (pd == null) return;

        // FIX: Jail bloquea la mayoría de comandos, pero permite una whitelist
        // de comandos seguros para que el jugador no quede totalmente incomunicado
        // y para que mods externos como Flan no sean bloqueados accidentalmente.
        if (pd.isJailActive()) {
            String cmdLower = command.toLowerCase().trim();

            // Whitelist: comandos permitidos durante el jail
            boolean isAllowed =
                cmdLower.startsWith("ticket")    ||   // sistema de tickets
                cmdLower.startsWith("flan")      ||   // mod de claims (Flan)
                cmdLower.startsWith("help")      ||   // ayuda
                cmdLower.startsWith("rules")     ||   // reglas del servidor
                cmdLower.startsWith("msg ")      ||   // mensaje privado al staff (para pedir ayuda)
                cmdLower.startsWith("tell ")     ||
                cmdLower.startsWith("w ");

            if (!isAllowed) {
                player.sendSystemMessage(Component.literal(
                    "§c[sᴛᴀꜰꜰ] Estás en la cárcel. No puedes usar ese comando. Expira: §e"
                    + PlayerData.formatExpiry(pd.jailExpiry)));
                ci.cancel();
                return;
            }
        }

        // Mute: bloquear comandos de mensajería privada
        if (pd.isMuteActive()) {
            String cmdLower = command.toLowerCase().trim();
            if (cmdLower.startsWith("msg ") || cmdLower.startsWith("tell ")
                || cmdLower.startsWith("w ") || cmdLower.startsWith("me ")
                || cmdLower.startsWith("r ") || cmdLower.startsWith("reply ")
                || cmdLower.startsWith("minecraft:msg ") || cmdLower.startsWith("minecraft:tell ")
                || cmdLower.startsWith("minecraft:w ") || cmdLower.startsWith("minecraft:me ")) {
                player.sendSystemMessage(Component.literal(
                    "§c[sᴛᴀꜰꜰ] Estás silenciado. No puedes enviar mensajes privados. Expira: §e"
                    + PlayerData.formatExpiry(pd.muteExpiry)));
                ci.cancel();
            }
        }
    }
}
