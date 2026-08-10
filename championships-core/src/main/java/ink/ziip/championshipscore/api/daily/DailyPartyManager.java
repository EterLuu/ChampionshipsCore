package ink.ziip.championshipscore.api.daily;

import org.bukkit.Bukkit;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Owns volatile parties and invitations. Queue mutation is delegated back to {@link DailyManager}. */
public final class DailyPartyManager {
    private static final long INVITE_SECONDS = 60L;
    private final DailyManager daily;
    private final Map<UUID, DailyParty> partyByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, Invite> inviteByTarget = new ConcurrentHashMap<>();

    DailyPartyManager(DailyManager daily) {
        this.daily = daily;
    }

    public synchronized @NotNull DailyParty getOrCreate(@NotNull UUID creator) {
        DailyParty existing = partyByPlayer.get(creator);
        if (existing != null) return existing;
        DailyParty party = new DailyParty(creator);
        partyByPlayer.put(creator, party);
        return party;
    }

    public @Nullable DailyParty getParty(@NotNull UUID player) {
        return partyByPlayer.get(player);
    }

    public synchronized boolean invite(@NotNull UUID sender, @NotNull UUID target) {
        DailyParty party = getOrCreate(sender);
        if (!party.isLeader(sender) || partyByPlayer.containsKey(target) || sender.equals(target)) return false;
        inviteByTarget.put(target, new Invite(party, Instant.now().plusSeconds(INVITE_SECONDS)));
        return true;
    }

    public synchronized @Nullable DailyParty accept(@NotNull UUID target) {
        Invite invite = inviteByTarget.remove(target);
        if (invite == null || invite.expires().isBefore(Instant.now()) || partyByPlayer.containsKey(target)) return null;
        DailyParty party = invite.party();
        if (daily.session(target) != null || daily.isQueued(target) || daily.isPartyInSession(party)) return null;
        daily.pauseParty(party, "同行小队成员发生变化");
        if (!party.add(target)) return null;
        partyByPlayer.put(target, party);
        return party;
    }

    public synchronized boolean leave(@NotNull UUID player) {
        DailyParty party = partyByPlayer.get(player);
        if (party == null || daily.isPartyInSession(party)) return false;
        daily.pauseParty(party, "同行小队成员发生变化");
        partyByPlayer.remove(player, party);
        party.remove(player);
        if (party.size() == 0) removeParty(party);
        return true;
    }

    public synchronized boolean disband(@NotNull UUID leader) {
        DailyParty party = partyByPlayer.get(leader);
        if (party == null || !party.isLeader(leader) || daily.isPartyInSession(party)) return false;
        daily.pauseParty(party, "同行小队已解散");
        removeParty(party);
        return true;
    }

    /** Called one tick after quit so Bukkit's online state is authoritative. */
    synchronized void handleOffline(@NotNull UUID player) {
        DailyParty party = partyByPlayer.get(player);
        if (party == null) return;
        daily.pauseParty(party, "有成员下线，排队已暂停");
        Set<UUID> members = party.members();
        UUID nextOnline = members.stream().filter(uuid -> Bukkit.getPlayer(uuid) != null).findFirst().orElse(null);
        if (nextOnline == null) {
            removeParty(party);
        } else if (party.isLeader(player)) {
            party.transferLeadership(nextOnline);
        }
    }

    public synchronized void clear() {
        inviteByTarget.clear();
        partyByPlayer.clear();
    }

    private void removeParty(DailyParty party) {
        for (UUID member : party.members()) partyByPlayer.remove(member, party);
        inviteByTarget.entrySet().removeIf(entry -> entry.getValue().party() == party);
    }

    private record Invite(DailyParty party, Instant expires) {}
}
