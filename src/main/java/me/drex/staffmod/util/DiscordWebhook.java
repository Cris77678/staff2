package me.drex.staffmod.util;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import me.drex.staffmod.StaffMod;
import me.drex.staffmod.config.DataStore; // Importamos el DataStore
import me.drex.staffmod.core.StaffModAsync;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class DiscordWebhook {

    public static void sendEmbed(String title, String description, int colorHex) {
        // Obtenemos la URL directamente de nuestro DataStore
        String webhookUrl = DataStore.discordWebhookUrl;

        // Validamos la URL usando la misma lógica que tenías para evitar errores
        if (webhookUrl == null || webhookUrl.isBlank() || webhookUrl.startsWith("AQUI_")) {
            StaffMod.LOGGER.warn("[StaffMod] Discord webhook no configurado. Omitiendo envío de: {}", title);
            return;
        }

        StaffModAsync.runAsync(() -> {
            try {
                URL url = URI.create(webhookUrl).toURL();
                HttpURLConnection conn = (HttpURLConnection) url.openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
                conn.setRequestProperty("User-Agent", "StaffMod/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(6000);
                conn.setReadTimeout(6000);

                JsonObject embed = new JsonObject();
                embed.addProperty("title",       title);
                embed.addProperty("description", description);
                embed.addProperty("color",       colorHex);
                embed.addProperty("timestamp",   java.time.Instant.now().toString());

                JsonArray embeds = new JsonArray();
                embeds.add(embed);

                JsonObject json = new JsonObject();
                json.add("embeds", embeds);
                json.addProperty("username", "StaffMod | Auditoría");

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(json.toString().getBytes(StandardCharsets.UTF_8));
                }

                int code = conn.getResponseCode();
                if (code == 204 || (code >= 200 && code < 300)) {
                    StaffMod.LOGGER.debug("[StaffMod] Discord webhook enviado OK (HTTP {})", code);
                } else if (code == 401 || code == 403) {
                    StaffMod.LOGGER.error("[StaffMod] Discord webhook: URL inválida o sin permisos (HTTP {}). Verifica la configuración en toggles.json.", code);
                } else if (code == 429) {
                    StaffMod.LOGGER.warn("[StaffMod] Discord webhook: Rate limit (HTTP 429). Demasiados mensajes.");
                } else {
                    StaffMod.LOGGER.warn("[StaffMod] Discord webhook respondió con código inesperado {}", code);
                }
                conn.disconnect();

            } catch (java.net.MalformedURLException e) {
                StaffMod.LOGGER.error("[StaffMod] Discord webhook: URL malformada '{}'", webhookUrl);
            } catch (Exception e) {
                StaffMod.LOGGER.error("[StaffMod] Error enviando webhook Discord:", e);
            }
        });
    }
}
