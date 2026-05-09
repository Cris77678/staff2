package me.drex.staffmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.gui.ActionExecutor;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class StaffChatCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("sc")
            .requires(src -> {
                try { return PermissionUtil.has(src.getPlayerOrException(), "staffmod.use"); }
                catch (Exception e) { return false; }
            })
            .executes(ctx -> {
                ServerPlayer player = ctx.getSource().getPlayerOrException();
                DataStore.toggleStaffChat(player.getUUID());
                boolean toggled = DataStore.isStaffChatToggled(player.getUUID());
                player.sendSystemMessage(Component.literal(toggled
                    ? "§a[sᴛᴀꜰꜰᴄʜᴀᴛ] Activado. Tus mensajes irán al canal de staff."
                    : "§c[sᴛᴀꜰꜰᴄʜᴀᴛ] Desactivado. Hablarás en el chat global."));
                return 1;
            })
            .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    String message = StringArgumentType.getString(ctx, "mensaje");
                    ActionExecutor.sendStaffChatMessage(player, message);
                    return 1;
                }))
        );
    }
}
