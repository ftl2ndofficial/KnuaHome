package com.ftl2nd;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;
import java.util.logging.Level;
import java.util.logging.Logger;
import org.jspecify.annotations.Nullable;

/**
 * High-throughput, thread-safe SQLite home storage with lock-free in-memory reads
 * and ordered single-thread WAL persistence (Folia-safe).
 */
public final class HomeDatabase implements AutoCloseable {

    private static final String UPSERT_SQL = """
        INSERT INTO player_homes (uuid, slot, world, x, y, z, yaw, pitch, custom_name, icon)
        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        ON CONFLICT(uuid, slot) DO UPDATE SET
            world = excluded.world,
            x = excluded.x,
            y = excluded.y,
            z = excluded.z,
            yaw = excluded.yaw,
            pitch = excluded.pitch,
            custom_name = excluded.custom_name,
            icon = excluded.icon;
        """;

    private static final String DELETE_SQL = "DELETE FROM player_homes WHERE uuid = ? AND slot = ?";
    private static final int MAX_BATCH_SIZE = 512;

    private final File dbFile;
    private final Logger logger;
    private final ConcurrentHashMap<UUID, ConcurrentHashMap<Integer, HomeLocation>> cache = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<SlotKey, CompletableFuture<Void>> pendingWrites = new ConcurrentHashMap<>(256);
    private final AtomicBoolean drainScheduled = new AtomicBoolean(false);
    private final AtomicLong committedBatches = new AtomicLong();
    private final AtomicLong committedRows = new AtomicLong();
    private final ExecutorService ioExecutor;
    private final ReentrantLock dbLock = new ReentrantLock();

    private Connection connection;
    private PreparedStatement upsertStatement;
    private PreparedStatement deleteStatement;

    public HomeDatabase(File dbFile, Logger logger) {
        this.dbFile = dbFile;
        this.logger = logger;
        this.ioExecutor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "KnuaHome-SQLite-Thread");
            t.setDaemon(true);
            return t;
        });
    }

    public void initialize() throws SQLException {
        File parent = dbFile.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        dbLock.lock();
        try {
            this.connection = DriverManager.getConnection("jdbc:sqlite:" + dbFile.getAbsolutePath());
            try (Statement stmt = connection.createStatement()) {
                stmt.execute("PRAGMA journal_mode=WAL;");
                stmt.execute("PRAGMA synchronous=NORMAL;");
                stmt.execute("PRAGMA temp_store=MEMORY;");
                stmt.execute("PRAGMA busy_timeout=5000;");
                stmt.execute("""
                    CREATE TABLE IF NOT EXISTS player_homes (
                        uuid TEXT NOT NULL,
                        slot INTEGER NOT NULL,
                        world TEXT NOT NULL,
                        x REAL NOT NULL,
                        y REAL NOT NULL,
                        z REAL NOT NULL,
                        yaw REAL NOT NULL,
                        pitch REAL NOT NULL,
                        custom_name TEXT,
                        icon TEXT DEFAULT 'WHITE_BED',
                        PRIMARY KEY (uuid, slot)
                    );
                    """);
                ensureColumnExists(stmt, "custom_name", "TEXT");
                ensureColumnExists(stmt, "icon", "TEXT DEFAULT 'WHITE_BED'");
            }
            this.upsertStatement = connection.prepareStatement(UPSERT_SQL);
            this.deleteStatement = connection.prepareStatement(DELETE_SQL);
            loadAllIntoCache();
            this.connection.setAutoCommit(false);
        } finally {
            dbLock.unlock();
        }
    }

    private static void ensureColumnExists(Statement stmt, String columnName, String columnDef) {
        try {
            stmt.execute("ALTER TABLE player_homes ADD COLUMN " + columnName + " " + columnDef + ";");
        } catch (SQLException ignored) {
            // Column already exists
        }
    }

    private void loadAllIntoCache() throws SQLException {
        cache.clear();
        String sql = "SELECT uuid, slot, world, x, y, z, yaw, pitch, custom_name, icon FROM player_homes";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                String rawUuid = rs.getString("uuid");
                int slot = rs.getInt("slot");
                if (rawUuid == null || slot < 1) {
                    continue;
                }
                UUID uuid;
                try {
                    uuid = UUID.fromString(rawUuid);
                } catch (IllegalArgumentException ignored) {
                    continue;
                }
                String world = rs.getString("world");
                if (world == null || world.isBlank()) {
                    continue;
                }
                HomeLocation loc = new HomeLocation(
                    world,
                    rs.getDouble("x"),
                    rs.getDouble("y"),
                    rs.getDouble("z"),
                    rs.getFloat("yaw"),
                    rs.getFloat("pitch"),
                    rs.getString("custom_name"),
                    rs.getString("icon")
                );
                cache.computeIfAbsent(uuid, k -> new ConcurrentHashMap<>(4)).put(slot, loc);
            }
        }
    }

    /**
     * Zero-copy O(1) read view of a player's homes for hot paths (Dialog & GUI rendering).
     */
    public Map<Integer, HomeLocation> getHomesView(UUID uuid) {
        if (uuid == null) {
            return Collections.emptyMap();
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        return (map == null || map.isEmpty()) ? Collections.emptyMap() : Collections.unmodifiableMap(map);
    }

    /**
     * Sorted snapshot copy of a player's homes ordered by slot ascending.
     */
    public Map<Integer, HomeLocation> getHomes(UUID uuid) {
        if (uuid == null) {
            return Collections.emptyMap();
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        if (map == null || map.isEmpty()) {
            return Collections.emptyMap();
        }
        return new TreeMap<>(map);
    }

    /**
     * Sorted list of set slot numbers for command tab-completion.
     */
    public List<Integer> getSortedSlots(UUID uuid) {
        if (uuid == null) {
            return Collections.emptyList();
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        if (map == null || map.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> slots = new ArrayList<>(map.keySet());
        Collections.sort(slots);
        return slots;
    }

    public @Nullable HomeLocation getHome(UUID uuid, int slot) {
        if (uuid == null || slot < 1) {
            return null;
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        return map != null ? map.get(slot) : null;
    }

    public int getHomeCount(UUID uuid) {
        if (uuid == null) {
            return 0;
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        return map != null ? map.size() : 0;
    }

    public int getCachedPlayerCount() {
        return cache.size();
    }

    public long getCommittedBatchCount() {
        return committedBatches.get();
    }

    public long getCommittedRowCount() {
        return committedRows.get();
    }

    public CompletableFuture<Void> setHome(UUID uuid, int slot, HomeLocation location) {
        if (uuid == null || slot < 1 || location == null) {
            return CompletableFuture.completedFuture(null);
        }
        cache.compute(uuid, (u, existingMap) -> {
            ConcurrentHashMap<Integer, HomeLocation> map = existingMap != null ? existingMap : new ConcurrentHashMap<>(4);
            map.put(slot, location);
            return map;
        });
        return enqueueSyncState(uuid, slot);
    }

    public boolean updateHomeName(UUID uuid, int slot, @Nullable String customName) {
        if (uuid == null || slot < 1) {
            return false;
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        if (map == null) {
            return false;
        }
        AtomicReference<HomeLocation> updatedRef = new AtomicReference<>();
        map.computeIfPresent(slot, (s, existing) -> {
            HomeLocation next = existing.withCustomName(customName);
            updatedRef.set(next);
            return next;
        });
        if (updatedRef.get() == null) {
            return false;
        }
        enqueueSyncState(uuid, slot);
        return true;
    }

    public boolean updateHomeIcon(UUID uuid, int slot, @Nullable String iconMaterial) {
        if (uuid == null || slot < 1) {
            return false;
        }
        ConcurrentHashMap<Integer, HomeLocation> map = cache.get(uuid);
        if (map == null) {
            return false;
        }
        AtomicReference<HomeLocation> updatedRef = new AtomicReference<>();
        map.computeIfPresent(slot, (s, existing) -> {
            HomeLocation next = existing.withIconMaterial(iconMaterial);
            updatedRef.set(next);
            return next;
        });
        if (updatedRef.get() == null) {
            return false;
        }
        enqueueSyncState(uuid, slot);
        return true;
    }

    public boolean deleteHome(UUID uuid, int slot) {
        if (uuid == null || slot < 1) {
            return false;
        }
        AtomicReference<HomeLocation> removedRef = new AtomicReference<>();
        cache.computeIfPresent(uuid, (u, map) -> {
            HomeLocation removed = map.remove(slot);
            if (removed != null) {
                removedRef.set(removed);
            }
            return map.isEmpty() ? null : map;
        });
        if (removedRef.get() == null) {
            return false;
        }
        enqueueSyncState(uuid, slot);
        return true;
    }

    /**
     * Enqueues (uuid, slot) into the coalescing dirty set and schedules a micro-batched SQLite WAL commit.
     * Rapid concurrent updates to the same slot coalesce automatically, and up to 512 player writes commit
     * inside a single SQLite transaction under heavy 500-player load.
     */
    private CompletableFuture<Void> enqueueSyncState(UUID uuid, int slot) {
        if (ioExecutor.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        SlotKey key = new SlotKey(uuid, slot);
        CompletableFuture<Void> future = pendingWrites.computeIfAbsent(key, k -> new CompletableFuture<>());
        if (drainScheduled.compareAndSet(false, true)) {
            try {
                ioExecutor.execute(this::drainPendingWrites);
            } catch (Throwable rejected) {
                drainScheduled.set(false);
                future.complete(null);
            }
        }
        return future;
    }

    private void drainPendingWrites() {
        do {
            drainScheduled.set(false);
            while (!pendingWrites.isEmpty()) {
                flushNextBatch();
            }
        } while (!pendingWrites.isEmpty() && drainScheduled.compareAndSet(false, true));
    }

    private void flushNextBatch() {
        List<BatchEntry> batch = new ArrayList<>(Math.min(MAX_BATCH_SIZE, Math.max(16, pendingWrites.size())));
        for (SlotKey key : pendingWrites.keySet()) {
            CompletableFuture<Void> fut = pendingWrites.remove(key);
            if (fut != null) {
                batch.add(new BatchEntry(key, fut));
                if (batch.size() >= MAX_BATCH_SIZE) {
                    break;
                }
            }
        }
        if (batch.isEmpty()) {
            return;
        }

        dbLock.lock();
        try {
            if (connection == null || connection.isClosed()) {
                return;
            }
            for (int i = 0, size = batch.size(); i < size; i++) {
                SlotKey key = batch.get(i).key();
                HomeLocation latest = getHome(key.uuid(), key.slot());
                if (latest != null) {
                    upsertStatement.setString(1, key.uuid().toString());
                    upsertStatement.setInt(2, key.slot());
                    upsertStatement.setString(3, latest.worldName());
                    upsertStatement.setDouble(4, latest.x());
                    upsertStatement.setDouble(5, latest.y());
                    upsertStatement.setDouble(6, latest.z());
                    upsertStatement.setFloat(7, latest.yaw());
                    upsertStatement.setFloat(8, latest.pitch());
                    upsertStatement.setString(9, latest.customName());
                    upsertStatement.setString(10, latest.iconMaterial());
                    upsertStatement.executeUpdate();
                } else {
                    deleteStatement.setString(1, key.uuid().toString());
                    deleteStatement.setInt(2, key.slot());
                    deleteStatement.executeUpdate();
                }
            }
            connection.commit();
            committedBatches.incrementAndGet();
            committedRows.addAndGet(batch.size());
        } catch (SQLException e) {
            try {
                if (connection != null && !connection.isClosed()) {
                    connection.rollback();
                }
            } catch (SQLException ignored) {
            }
            logger.log(Level.SEVERE, "Failed to commit SQLite home batch (" + batch.size() + " entries)", e);
        } finally {
            dbLock.unlock();
            for (int i = 0, size = batch.size(); i < size; i++) {
                batch.get(i).future().complete(null);
            }
        }
    }

    /**
     * Waits for all queued async SQLite operations submitted so far to complete.
     */
    public CompletableFuture<Void> flush() {
        if (ioExecutor.isShutdown()) {
            return CompletableFuture.completedFuture(null);
        }
        return CompletableFuture.runAsync(this::drainPendingWrites, ioExecutor);
    }

    @Override
    public void close() {
        ioExecutor.shutdown();
        try {
            ioExecutor.awaitTermination(10, TimeUnit.SECONDS);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
        drainPendingWrites();
        dbLock.lock();
        try {
            closeQuietly(upsertStatement);
            closeQuietly(deleteStatement);
            upsertStatement = null;
            deleteStatement = null;
            if (connection != null && !connection.isClosed()) {
                connection.close();
            }
        } catch (SQLException e) {
            logger.log(Level.SEVERE, "Failed to close SQLite connection", e);
        } finally {
            dbLock.unlock();
        }
    }

    private static void closeQuietly(@Nullable Statement stmt) {
        if (stmt != null) {
            try {
                stmt.close();
            } catch (SQLException ignored) {
            }
        }
    }

    private record SlotKey(UUID uuid, int slot) {
    }

    private record BatchEntry(SlotKey key, CompletableFuture<Void> future) {
    }
}
