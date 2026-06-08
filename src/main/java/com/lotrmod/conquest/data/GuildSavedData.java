package com.lotrmod.conquest.data;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.*;

public class GuildSavedData extends SavedData {

    public static final Factory<GuildSavedData> FACTORY = new Factory<>(
        GuildSavedData::new,
        GuildSavedData::load,
        DataFixTypes.SAVED_DATA_MAP_DATA
    );

    private final Map<UUID, Guild> guilds       = new LinkedHashMap<>();
    private final Map<Long, UUID> chunkIndex    = new HashMap<>();   // ChunkPos.toLong() -> guildId
    private final Map<String, UUID> nameIndex   = new HashMap<>();   // name.lower -> guildId
    private final Map<UUID, UUID> playerIndex   = new HashMap<>();   // playerUUID -> guildId

    /** Running total of ticks where ≥1 player was online. Drives all in-game timers. */
    public long onlineTicks = 0L;

    public GuildSavedData() {}

    public static GuildSavedData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "lotrmod_guilds");
    }

    public long onlineDay() { return onlineTicks / 24000L; }

    // ── Guild CRUD ───────────────────────────────────────────────────────────

    public void addGuild(Guild guild) {
        guilds.put(guild.id, guild);
        nameIndex.put(guild.name.toLowerCase(Locale.ROOT), guild.id);
        for (UUID uuid : guild.memberUUIDs) playerIndex.put(uuid, guild.id);
        for (ChunkPos cp : guild.claimedChunks) chunkIndex.put(cp.toLong(), guild.id);
        setDirty();
    }

    public void removeGuild(UUID guildId) {
        Guild g = guilds.remove(guildId);
        if (g == null) return;
        nameIndex.remove(g.name.toLowerCase(Locale.ROOT));
        for (UUID uuid : g.memberUUIDs) playerIndex.remove(uuid);
        for (ChunkPos cp : g.claimedChunks) chunkIndex.remove(cp.toLong());
        setDirty();
    }

    public Guild getGuild(UUID id)                        { return guilds.get(id); }
    public Guild getGuildByName(String name)              { UUID id = nameIndex.get(name.toLowerCase(Locale.ROOT)); return id == null ? null : guilds.get(id); }
    public Guild getGuildForPlayer(UUID playerUUID)       { UUID id = playerIndex.get(playerUUID); return id == null ? null : guilds.get(id); }
    public Guild getChunkOwner(ChunkPos cp)               { UUID id = chunkIndex.get(cp.toLong()); return id == null ? null : guilds.get(id); }
    public Collection<Guild> allGuilds()                  { return Collections.unmodifiableCollection(guilds.values()); }
    public boolean isNameTaken(String name)               { return nameIndex.containsKey(name.toLowerCase(Locale.ROOT)); }

    /** Call after modifying guild membership. */
    public void refreshPlayerIndex(Guild guild) {
        playerIndex.entrySet().removeIf(e -> e.getValue().equals(guild.id));
        for (UUID uuid : guild.memberUUIDs) playerIndex.put(uuid, guild.id);
        setDirty();
    }

    /** Call after modifying guild.banners / guild.claimedChunks. */
    public void refreshChunkIndex(Guild guild) {
        chunkIndex.entrySet().removeIf(e -> e.getValue().equals(guild.id));
        for (ChunkPos cp : guild.claimedChunks) chunkIndex.put(cp.toLong(), guild.id);
        setDirty();
    }

    // ── Serialization ────────────────────────────────────────────────────────

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider provider) {
        tag.putLong("onlineTicks", onlineTicks);
        ListTag list = new ListTag();
        for (Guild g : guilds.values()) list.add(g.save());
        tag.put("guilds", list);
        return tag;
    }

    public static GuildSavedData load(CompoundTag tag, HolderLookup.Provider provider) {
        GuildSavedData data = new GuildSavedData();
        data.onlineTicks = tag.getLong("onlineTicks");
        ListTag list = tag.getList("guilds", Tag.TAG_COMPOUND);
        for (Tag t : list) {
            Guild g = Guild.load((CompoundTag) t);
            data.guilds.put(g.id, g);
            data.nameIndex.put(g.name.toLowerCase(Locale.ROOT), g.id);
            for (UUID uuid : g.memberUUIDs) data.playerIndex.put(uuid, g.id);
            for (ChunkPos cp : g.claimedChunks) data.chunkIndex.put(cp.toLong(), g.id);
        }
        return data;
    }
}
