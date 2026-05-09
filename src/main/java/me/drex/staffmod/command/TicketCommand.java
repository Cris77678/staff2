package me.drex.staffmod.command;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.TicketEntry;
import me.drex.staffmod.util.PermissionUtil;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public class TicketCommand {

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("ticket")
            .then(Commands.argument("mensaje", StringArgumentType.greedyString())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();

                    boolean hasOpen = DataStore.getAllTickets().stream()
                        .anyMatch(t -> t.creatorUuid.equals(player.getUUID())
                            && ("ABIERTO".equals(t.status) || "TOMADO".equals(t.status)));

                    if (hasOpen) {
                        player.sendSystemMessage(Component.literal(
                            "§c[ᴛɪᴄᴋᴇᴛs] Ya tienes un ticket abierto. Espera a que sea cerrado antes de crear otro."));
                        return 1;
                    }

                    String message = StringArgumentType.getString(ctx, "mensaje");
                    if (message.length() < 5) {
                        player.sendSystemMessage(Component.literal(
                            "§c[ᴛɪᴄᴋᴇᴛs] El mensaje es demasiado corto. Describe tu problema."));
                        return 1;
                    }

                    TicketEntry ticket = DataStore.createTicket(
                        player.getUUID(), player.getName().getString(), message);

                    // Notificación a Discord
                    me.drex.staffmod.util.DiscordWebhook.sendEmbed(
                        "Nuevo Ticket: #" + ticket.id,
                        "**Jugador:** " + player.getName().getString() + "\n**Mensaje:** " + message,
                        0x00AAFF // Color azul
                    );

                    player.sendSystemMessage(Component.literal(
                        "§a[ᴛɪᴄᴋᴇᴛs] Tu ticket §b(#" + ticket.id + ")§a fue enviado al staff."));

                    for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                        if (PermissionUtil.has(p, "staffmod.use") && DataStore.isOnDuty(p.getUUID())) {
                            p.sendSystemMessage(Component.literal(
                                "§e[ᴛɪᴄᴋᴇᴛs] §fNuevo ticket §b#" + ticket.id
                                + "§f de §b" + player.getName().getString() + "§f: §7" + message));
                            p.sendSystemMessage(Component.literal("§7Usa §e/ticket reply " + ticket.id + " <mensaje> §7para responder o §e/staff §7→ Tickets para atenderlo."));
                        }
                    }

                    return 1;
                }))
            .then(Commands.literal("reply")
                .then(Commands.argument("id", IntegerArgumentType.integer())
                .then(Commands.argument("respuesta", StringArgumentType.greedyString())
                    .executes(ctx -> {
                        ServerPlayer player = ctx.getSource().getPlayerOrException();
                        int id = IntegerArgumentType.getInteger(ctx, "id");
                        String reply = StringArgumentType.getString(ctx, "respuesta");
                        
                        TicketEntry ticket = DataStore.getTicket(id);
                        if (ticket == null || "CERRADO".equals(ticket.status)) {
                            player.sendSystemMessage(Component.literal("§c[ᴛɪᴄᴋᴇᴛs] El ticket no existe o está cerrado."));
                            return 0;
                        }

                        boolean isStaff = PermissionUtil.has(player, "staffmod.use");
                        
                        if (!isStaff && !ticket.creatorUuid.equals(player.getUUID())) {
                            player.sendSystemMessage(Component.literal("§c[ᴛɪᴄᴋᴇᴛs] No tienes permiso para responder a este ticket."));
                            return 0;
                        }

                        String format = (isStaff ? "§c[sᴛᴀꜰꜰ] §f" : "§b[ᴊᴜɢᴀᴅᴏʀ] §f") + player.getName().getString() + "§7: §e" + reply;
                        ticket.replies.add(format);
                        
                        if (isStaff) {
                            ticket.hasUnreadReply = true;
                            ticket.status = "TOMADO";
                            
                            ServerPlayer target = player.getServer().getPlayerList().getPlayer(ticket.creatorUuid);
                            if (target != null) {
                                target.sendSystemMessage(Component.literal(" "));
                                target.sendSystemMessage(Component.literal("§a§l¡Tienes una nueva respuesta en tu ticket #" + ticket.id + "!"));
                                target.sendSystemMessage(Component.literal(format));
                                target.sendSystemMessage(Component.literal("§7Usa §e/ticket reply " + ticket.id + " <mensaje> §7para contestar."));
                                target.sendSystemMessage(Component.literal(" "));
                                ticket.hasUnreadReply = false;
                            }
                        } else {
                            for (ServerPlayer p : player.getServer().getPlayerList().getPlayers()) {
                                if (PermissionUtil.has(p, "staffmod.use") && DataStore.isOnDuty(p.getUUID())) {
                                    p.sendSystemMessage(Component.literal("§e[ᴛɪᴄᴋᴇᴛs] §fEl jugador respondió al ticket §b#" + ticket.id + "§f:"));
                                    p.sendSystemMessage(Component.literal(format));
                                }
                            }
                        }
                        
                        DataStore.updateTicket(ticket);
                        player.sendSystemMessage(Component.literal("§a[ᴛɪᴄᴋᴇᴛs] Respuesta añadida al ticket #" + id));
                        return 1;
                    }))))
        );
    }
}
