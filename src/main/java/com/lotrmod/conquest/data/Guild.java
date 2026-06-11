package com.lotrmod.conquest.data;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.*;
import net.minecraft.world.level.ChunkPos;

import java.util.*;

public class Guild {
    public final UUID id;
    public String name;
    public String tag;
    public UUID masterUUID;
    public final Set<UUID> officerUUIDs = new HashSet<>();
    /** All members including master and officers. */
    public final Set<UUID> memberUUIDs = new HashSet<>();
    public JoinMode joinMode = JoinMode.INVITE_ONLY;
    public String factionId = "default";

    /** Resource pool: 6 tracked resources. */
    public final Map<TreasuryResource, Long> treasury = new EnumMap<>(TreasuryResource.class);

    /** Banner base pos → set of 9 claimed ChunkPos. */
    public final Map<BlockPos, Set<ChunkPos>> banners = new LinkedHashMap<>();

    /** Fast lookup of all claimed chunks (union of all banner claim sets). */
    public final Set<ChunkPos> claimedChunks = new HashSet<>();

    /** Active wars: opponentGuildId → WarState. */
    public final Map<UUID, WarState> wars = new HashMap<>();

    /** Pending disband votes. */
    public final Set<UUID> disbandVotes = new HashSet<>();
    public boolean disbandVoteActive = false;

    /** Online-day of last successful upkeep charge. */
    public long lastUpkeepDay = -1L;
    public boolean inGracePeriod = false;
    public long gracePeriodStartDay = -1L;
    /** Online-tick when the last outermost banner was dropped (for hourly drop rate). */
    public long lastBannerDropTick = 0L;

    /** Pending invite UUIDs. */
    public final Set<UUID> pendingInvites = new HashSet<>();

    /** Players who were kicked — barred from rejoining until re-invited. */
    public final Set<UUID> kickedUUIDs = new HashSet<>();

    /** Guild ids that have offered us a peace treaty and are awaiting our accept/decline. */
    public final Set<UUID> peaceRequestsReceived = new HashSet<>();

    /** Block-placement dev score inside claimed chunks (approximates development tier). */
    public long developmentScore = 0L;

    public Guild(UUID id, String name, String tag, UUID masterUUID) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.masterUUID = masterUUID;
        this.memberUUIDs.add(masterUUID);
        for (TreasuryResource r : TreasuryResource.values()) {
            treasury.put(r, 0L);
        }
    }

    public boolean isMember(UUID uuid)  { return memberUUIDs.contains(uuid); }
    public boolean isOfficer(UUID uuid) { return officerUUIDs.contains(uuid); }
    public boolean isMaster(UUID uuid)  { return masterUUID.equals(uuid); }
    public boolean canManage(UUID uuid) { return isMaster(uuid) || isOfficer(uuid); }

    public int bannerCount() { return banners.size(); }

    public void addBanner(BlockPos pos, Set<ChunkPos> chunks) {
        banners.put(pos, new HashSet<>(chunks));
        claimedChunks.addAll(chunks);
    }

    public void removeBanner(BlockPos pos) {
        banners.remove(pos);
        rebuildClaimedChunks();
    }

    private void rebuildClaimedChunks() {
        claimedChunks.clear();
        for (Set<ChunkPos> s : banners.values()) claimedChunks.addAll(s);
    }

    public BlockPos bannerCentroid() {
        if (banners.isEmpty()) return BlockPos.ZERO;
        long sx = 0, sy = 0, sz = 0;
        for (BlockPos p : banners.keySet()) { sx += p.getX(); sy += p.getY(); sz += p.getZ(); }
        int n = banners.size();
        return new BlockPos((int)(sx / n), (int)(sy / n), (int)(sz / n));
    }

    /** Returns banner positions sorted outermost (furthest from centroid) first. */
    public List<BlockPos> bannersOutermostFirst() {
        BlockPos centroid = bannerCentroid();
        List<BlockPos> list = new ArrayList<>(banners.keySet());
        list.sort((a, b) -> Double.compare(b.distSqr(centroid), a.distSqr(centroid)));
        return list;
    }

    public boolean canAffordUpkeep() {
        int bracket = TreasuryResource.upkeepBracket(bannerCount());
        if (bracket == 0) return true;
        int cost = TreasuryResource.upkeepCostForBracket(bracket);
        for (TreasuryResource r : TreasuryResource.values()) {
            if (treasury.getOrDefault(r, 0L) < cost) return false;
        }
        return true;
    }

    public void chargeUpkeep() {
        int bracket = TreasuryResource.upkeepBracket(bannerCount());
        if (bracket == 0) return;
        int cost = TreasuryResource.upkeepCostForBracket(bracket);
        for (TreasuryResource r : TreasuryResource.values()) {
            treasury.merge(r, (long) -cost, (a, b) -> Math.max(0, a + b));
        }
    }

    // ── NBT ──────────────────────────────────────────────────────────────────

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putString("name", name);
        tag.putString("tag", this.tag);
        tag.putUUID("master", masterUUID);
        tag.putString("joinMode", joinMode.name());
        tag.putString("faction", factionId);
        tag.putLong("lastUpkeepDay", lastUpkeepDay);
        tag.putBoolean("gracePeriod", inGracePeriod);
        tag.putLong("gracePeriodStart", gracePeriodStartDay);
        tag.putLong("lastBannerDropTick", lastBannerDropTick);
        tag.putBoolean("disbandVoteActive", disbandVoteActive);
        tag.putLong("devScore", developmentScore);

        tag.put("officers", saveUUIDs(officerUUIDs));
        tag.put("members", saveUUIDs(memberUUIDs));
        tag.put("invites", saveUUIDs(pendingInvites));
        tag.put("kicked", saveUUIDs(kickedUUIDs));
        tag.put("peaceRequests", saveUUIDs(peaceRequestsReceived));
        tag.put("disbandVotes", saveUUIDs(disbandVotes));

        CompoundTag treas = new CompoundTag();
        for (Map.Entry<TreasuryResource, Long> e : treasury.entrySet()) {
            treas.putLong(e.getKey().name(), e.getValue());
        }
        tag.put("treasury", treas);

        ListTag bannerList = new ListTag();
        for (Map.Entry<BlockPos, Set<ChunkPos>> e : banners.entrySet()) {
            CompoundTag bt = new CompoundTag();
            bt.putLong("pos", e.getKey().asLong());
            long[] chunkLongs = e.getValue().stream().mapToLong(ChunkPos::toLong).toArray();
            bt.putLongArray("chunks", chunkLongs);
            bannerList.add(bt);
        }
        tag.put("banners", bannerList);

        ListTag warList = new ListTag();
        for (WarState ws : wars.values()) warList.add(ws.save());
        tag.put("wars", warList);

        return tag;
    }

    public static Guild load(CompoundTag tag) {
        UUID id     = tag.getUUID("id");
        String name = tag.getString("name");
        String gt   = tag.getString("tag");
        UUID master = tag.getUUID("master");
        Guild g = new Guild(id, name, gt, master);

        g.joinMode              = JoinMode.valueOf(tag.getString("joinMode"));
        g.factionId             = tag.getString("faction");
        g.lastUpkeepDay         = tag.getLong("lastUpkeepDay");
        g.inGracePeriod         = tag.getBoolean("gracePeriod");
        g.gracePeriodStartDay   = tag.getLong("gracePeriodStart");
        g.lastBannerDropTick    = tag.getLong("lastBannerDropTick");
        g.disbandVoteActive     = tag.getBoolean("disbandVoteActive");
        g.developmentScore      = tag.getLong("devScore");

        loadUUIDs(tag.getList("officers",     Tag.TAG_COMPOUND), g.officerUUIDs);
        g.memberUUIDs.clear();
        loadUUIDs(tag.getList("members",      Tag.TAG_COMPOUND), g.memberUUIDs);
        loadUUIDs(tag.getList("invites",      Tag.TAG_COMPOUND), g.pendingInvites);
        loadUUIDs(tag.getList("kicked",       Tag.TAG_COMPOUND), g.kickedUUIDs);
        loadUUIDs(tag.getList("peaceRequests",Tag.TAG_COMPOUND), g.peaceRequestsReceived);
        loadUUIDs(tag.getList("disbandVotes", Tag.TAG_COMPOUND), g.disbandVotes);

        CompoundTag treas = tag.getCompound("treasury");
        for (TreasuryResource r : TreasuryResource.values()) {
            g.treasury.put(r, treas.contains(r.name()) ? treas.getLong(r.name()) : 0L);
        }

        ListTag bannerList = tag.getList("banners", Tag.TAG_COMPOUND);
        for (Tag bt : bannerList) {
            CompoundTag b   = (CompoundTag) bt;
            BlockPos pos    = BlockPos.of(b.getLong("pos"));
            Set<ChunkPos> chunks = new HashSet<>();
            for (long l : b.getLongArray("chunks")) chunks.add(new ChunkPos(l));
            g.banners.put(pos, chunks);
            g.claimedChunks.addAll(chunks);
        }

        ListTag warList = tag.getList("wars", Tag.TAG_COMPOUND);
        for (Tag wt : warList) {
            WarState ws = WarState.load((CompoundTag) wt);
            g.wars.put(ws.opponentGuildId, ws);
        }

        return g;
    }

    private static ListTag saveUUIDs(Set<UUID> set) {
        ListTag list = new ListTag();
        for (UUID u : set) {
            CompoundTag t = new CompoundTag();
            t.putUUID("v", u);
            list.add(t);
        }
        return list;
    }

    private static void loadUUIDs(ListTag list, Set<UUID> target) {
        for (Tag t : list) target.add(((CompoundTag) t).getUUID("v"));
    }
}
