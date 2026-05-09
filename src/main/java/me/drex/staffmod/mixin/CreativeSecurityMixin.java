package me.drex.staffmod.mixin;

import me.drex.staffmod.features.BuilderManager;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Set;

@Mixin(ServerGamePacketListenerImpl.class)
public abstract class CreativeSecurityMixin {

    @Shadow
    public ServerPlayer player;

    // Ítem bloqueados en Builder Mode
    private static final Set<String> BLACKLISTED = Set.of(
        "minecraft:bedrock",
        "minecraft:command_block",
        "minecraft:chain_command_block",
        "minecraft:repeating_command_block",
        "minecraft:command_block_minecart",
        "minecraft:structure_block",
        "minecraft:structure_void",
        "minecraft:jigsaw",
        "minecraft:barrier",
        "minecraft:light",
        "minecraft:debug_stick"
    );

    @Inject(method = "handleSetCreativeModeSlot", at = @At("HEAD"), cancellable = true)
    private void staffmod$blockBlacklistedCreative(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        if (!BuilderManager.isBuilderMode(player.getUUID())) return;

        ItemStack stack = packet.itemStack();
        if (stack.isEmpty()) return;

        String itemId = BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
        if (BLACKLISTED.contains(itemId)) {
            player.sendSystemMessage(Component.literal(
                "§c[ʙᴜɪʟᴅᴇʀ] §fEse ítem está bloqueado en modo constructor: §e" + itemId));
            // Enviar slot vacío de vuelta para rechazar
            player.inventoryMenu.sendAllDataToRemote();
            ci.cancel();
        }
    }
}
