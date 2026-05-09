package me.drex.staffmod.core;

import me.drex.staffmod.StaffMod;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Pool de hilos centralizado para operaciones async del mod.
 * MAIN THREAD: world, entities, inventarios, GUIs
 * ASYNC: archivos, DB, logs
 * PUENTE: server.execute(() -> {}) para volver al main thread
 */
public class StaffModAsync {

    private static final int POOL_SIZE = Math.max(2, Math.min(4, Runtime.getRuntime().availableProcessors() / 2));

    private static final AtomicInteger WORKER_ID = new AtomicInteger(0);

    private static final ExecutorService WORKER_POOL = Executors.newFixedThreadPool(
        POOL_SIZE,
        r -> {
            Thread t = new Thread(r, "StaffMod-Worker-" + WORKER_ID.getAndIncrement());
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                StaffMod.LOGGER.error("[StaffMod] Excepción no controlada en {}: ", thread.getName(), ex));
            return t;
        }
    );

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(
        r -> {
            Thread t = new Thread(r, "StaffMod-Scheduler");
            t.setDaemon(true);
            t.setUncaughtExceptionHandler((thread, ex) ->
                StaffMod.LOGGER.error("[StaffMod] Excepción en Scheduler: ", ex));
            return t;
        }
    );

    /** Ejecuta una tarea en hilo async (I/O, DB, logs). NO acceder al mundo desde aquí. */
    public static void runAsync(Runnable task) {
        WORKER_POOL.execute(() -> {
            long start = System.currentTimeMillis();
            try {
                task.run();
            } catch (Exception e) {
                StaffMod.LOGGER.error("[StaffMod] Error en worker async:", e);
            }
            long elapsed = System.currentTimeMillis() - start;
            if (elapsed > 50) {
                StaffMod.LOGGER.warn("[StaffMod] Tarea async tomó {}ms (>50ms detectado).", elapsed);
            }
        });
    }

    /** Programa una tarea repetitiva async. */
    public static void scheduleAsync(Runnable task, long delay, long period, TimeUnit unit) {
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                task.run();
            } catch (Exception e) {
                StaffMod.LOGGER.error("[StaffMod] Error en tarea programada:", e);
            }
        }, delay, period, unit);
    }

    /** Programa una tarea con ejecución única diferida. */
    public static void scheduleOnce(Runnable task, long delay, TimeUnit unit) {
        SCHEDULER.schedule(() -> {
            try {
                task.run();
            } catch (Exception e) {
                StaffMod.LOGGER.error("[StaffMod] Error en tarea diferida:", e);
            }
        }, delay, unit);
    }

    /** Apagado limpio. Espera máx 5 segundos antes de forzar cierre. */
    public static void shutdown() {
        StaffMod.LOGGER.info("[StaffMod] Deteniendo hilos asíncronos...");
        SCHEDULER.shutdown();
        WORKER_POOL.shutdown();
        try {
            if (!WORKER_POOL.awaitTermination(5, TimeUnit.SECONDS)) {
                WORKER_POOL.shutdownNow();
                StaffMod.LOGGER.warn("[StaffMod] Forzado cierre del pool de hilos.");
            }
        } catch (InterruptedException e) {
            WORKER_POOL.shutdownNow();
            Thread.currentThread().interrupt();
        }
        StaffMod.LOGGER.info("[StaffMod] Hilos detenidos correctamente.");
    }
}
