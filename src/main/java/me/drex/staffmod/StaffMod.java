package me.drex.staffmod;

import me.drex.staffmod.command.KitCommand;
import me.drex.staffmod.command.StaffChatCommand;
import me.drex.staffmod.command.StaffCommand;
import me.drex.staffmod.command.TicketCommand;
import me.drex.staffmod.config.DataStore;
import me.drex.staffmod.config.PlayerData;
import me.drex.staffmod.config.RankManager;
import me.drex.staffmod.core.StaffModAsync;
import me.drex.staffmod.data.DatabaseManager;
import me.drex.staffmod.features.AntiSpamFilter;
import me.drex.staffmod.features.BuilderManager;
import me.drex.staffmod.features.CobblemonAlerts;
import me.drex.staffmod.features.KitManager;
import me.drex.staffmod.features.StaffConnectionHandler;
import me.drex.staffmod.features.VanishManager;
import me.drex.staffmod.gui.ActionExecutor;
import me.drex.staffmod.logging.AuditLogManager;
import me.drex.staffmod.punishment.ExpirationTask;
import me.drex.staffmod.util.PermissionUtil;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

public class StaffMod implements ModInitializer {

    public static final String MOD_ID = "staffmod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);
    public static MinecraftServer SERVER;

    @Override
    public void onInitialize() {
        LOGGER.info("[StaffMod] ɪɴɪᴄɪᴀɴᴅᴏ sɪsᴛᴇᴍᴀ ᴘʀᴇᴍɪᴜᴍ ᴅᴇ sᴛᴀꜰꜰ...");

        // Carga de datos (debe ocurrir antes que cualquier otra cosa)
        DataStore.load();

        // Registro de comandos
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            StaffCommand.register(dispatcher);
            TicketCommand.register(dispatcher);
            StaffChatCommand.register(dispatcher);
            KitCommand.register(dispatcher);
        });

        // ── Evento de chat: anti-spam ──────────────────
        ServerMessageEvents.ALLOW_CHAT_MESSAGE.register((message, sender, params) -> {
            // Filtro anti-spam
            return AntiSpamFilter.checkChat(sender, message.signedContent());
        });

        // ── Evento de desconexión ────────────────────────────────────────
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.player;
            PlayerData pd = DataStore.get(player.getUUID());

            // Alerta si un jugador se va estando congelado
            if (pd != null && pd.frozen) {
                for (ServerPlayer p : server.getPlayerList().getPlayers()) {
                    if (PermissionUtil.has(p, "staffmod.use")) {
                        p.sendSystemMessage(Component.literal(
                            "§4§l[ALERTA] §cEl jugador §f" + player.getName().getString()
                            + " §cse desconectó mientras estaba CONGELADO."));
                    }
                }
                me.drex.staffmod.util.DiscordWebhook.sendEmbed(
                    "Evasión de Revisión (Freeze)",
                    "El jugador **" + player.getName().getString() + "** se desconectó estando congelado.",
                    0xFF5555);
            }

            // Limpiar estado de turno al salir
            DataStore.setDuty(player.getUUID(), false);
        });

        // ── Inicio del servidor ──────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STARTED.register(server -> {
            SERVER = server;
            LOGGER.info("[StaffMod] Servidor iniciando — conectando módulos...");

            PermissionUtil.init();
            RankManager.loadRanks();
            KitManager.load();

            // Inicialización del BuilderManager para persistencia de inventarios
            BuilderManager.init(FabricLoader.getInstance().getConfigDir());

            // Base de datos SQLite
            StaffModAsync.runAsync(() -> {
                try {
                    DatabaseManager.init();
                    LOGGER.info("[StaffMod] Base de datos SQLite inicializada.");
                } catch (Exception e) {
                    LOGGER.error("[StaffMod] Error iniciando base de datos:", e);
                }
            });

            // Tarea de expiración de castigos cada 10 segundos
            StaffModAsync.scheduleAsync(new ExpirationTask(server), 10, 10, TimeUnit.SECONDS);

            // Alertas de Cobblemon
            try {
                CobblemonAlerts.registerEvents();
            } catch (NoClassDefFoundError | Exception e) {
                LOGGER.warn("[StaffMod] API de Cobblemon no detectada. Módulo desactivado de forma segura.");
            }
        });

        // ── Parada del servidor ──────────────────────────────────────────
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            LOGGER.info("[StaffMod] Servidor deteniéndose. Guardando datos...");
            DataStore.save();
            KitManager.saveAllAsync();
            AuditLogManager.save();
            DatabaseManager.close();
            StaffModAsync.shutdown();
        });

        // Tick del servidor para anuncios automáticos
        ServerTickEvents.END_SERVER_TICK.register(DataStore::tickAnnouncements);

        // ── Conexión de jugadores ────────────────────────────────────────
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            PlayerData pd = DataStore.get(handler.player.getUUID());

            // Verificar ban al unirse
            if (pd != null && pd.isBanActive()) {
                handler.disconnect(Component.literal(
                    "§cEstás baneado del servidor.\n§fRazón: §e" + pd.banReason
                    + "\n§fExpira: §e" + PlayerData.formatExpiry(pd.banExpiry)));
                return;
            }

            // Aplicar estados persistidos y restaurar modos (builder)
            DataStore.applyOnJoin(handler.player);
            BuilderManager.applyBuilderOnJoin(handler.player);

            // Vanish: diferir 1 tick para que el servidor haya terminado de enviar
            // el paquete PlayerInfoUpdate de join a todos los clientes ANTES de
            // que mandemos el PlayerInfoRemove. Si se aplica en el mismo tick,
            // el InfoUpdate llega después del InfoRemove y el jugador queda visible en el tab.
            server.execute(() -> VanishManager.applyVanishOnJoin(handler.player));

            // Notificaciones de bienvenida al staff
            StaffConnectionHandler.onStaffJoin(handler.player);
        });

        LOGGER.info("[StaffMod] ᴛᴏᴅᴏs ʟᴏs ᴍóᴅᴜʟᴏs ᴀᴄᴛɪᴠᴏs.");
    }
}
