package at.marcelr.mradmin.hook;

import space.arim.libertybans.api.LibertyBans;
import space.arim.libertybans.api.NetworkAddress;
import space.arim.libertybans.api.PlayerOperator;
import space.arim.libertybans.api.PlayerVictim;
import space.arim.libertybans.api.PunishmentType;
import space.arim.libertybans.api.Victim;
import space.arim.libertybans.api.punish.Punishment;
import space.arim.libertybans.api.select.SelectionPredicate;
import space.arim.libertybans.api.select.SortPunishments;
import space.arim.omnibus.OmnibusProvider;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * The only class that uses LibertyBans' API - created by {@link Bans#get} while LibertyBans runs. The API answers
 * asynchronously; the web threads wait at most a few seconds.
 */
final class LibertyBansAccess implements Bans {

    private static final long TIMEOUT_SECONDS = 10;

    private final LibertyBans api;

    LibertyBansAccess() {
        api = OmnibusProvider.getOmnibus().getRegistry().getProvider(LibertyBans.class)
                .orElseThrow(() -> new IllegalStateException("LibertyBans has not registered its API"));
    }

    @Override
    public List<Entry> history(UUID player, int limit) throws Exception {
        Victim victim = PlayerVictim.of(player);
        List<Punishment> all = api.getSelector().selectionBuilder().victim(victim).selectAll().limitToRetrieve(limit)
                .build().getAllSpecificPunishments(SortPunishments.NEWEST_FIRST)
                .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // Which of them are still in force (not expired and not revoked).
        Set<Long> active = new HashSet<>();
        for (Punishment p : api.getSelector().selectionBuilder().victim(victim).selectActiveOnly().build()
                .getAllSpecificPunishments().toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            active.add(p.getIdentifier());
        }
        List<Entry> result = new ArrayList<>();
        for (Punishment p : all) {
            result.add(entry(p, active.contains(p.getIdentifier())));
        }
        return result;
    }

    @Override
    public Entry activeBan(long id) throws Exception {
        return api.getSelector().getActivePunishmentByIdAndType(id, PunishmentType.BAN).toCompletableFuture()
                .get(TIMEOUT_SECONDS, TimeUnit.SECONDS).map(p -> entry(p, true)).orElse(null);
    }

    @Override
    public Entry applicableBan(UUID player, java.net.InetAddress address) throws Exception {
        return api.getSelector().getApplicablePunishment(player, NetworkAddress.of(address), PunishmentType.BAN)
                .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS).map(p -> entry(p, true)).orElse(null);
    }

    @Override
    public UUID lookup(String name) throws Exception {
        return api.getUserResolver().lookupUUID(name).get(TIMEOUT_SECONDS, TimeUnit.SECONDS).orElse(null);
    }

    private static Entry entry(Punishment p, boolean active) {
        UUID operator = p.getOperator() instanceof PlayerOperator op ? op.getUUID() : null;
        return new Entry(p.getIdentifier(), name(p.getType()), p.getReason(), operator, String.valueOf(p.getScope()),
                p.getStartDate().toEpochMilli(), p.isPermanent() ? 0 : p.getEndDate().toEpochMilli(), active);
    }

    @Override
    public Map<UUID, Set<String>> activeBansAndMutes() throws Exception {
        Map<UUID, Set<String>> result = new HashMap<>();
        for (Punishment p : api.getSelector().selectionBuilder()
                .types(SelectionPredicate.matchingAnyOf(PunishmentType.BAN, PunishmentType.MUTE))
                .selectActiveOnly().build().getAllSpecificPunishments()
                .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            if (p.getVictim() instanceof PlayerVictim victim) {
                result.computeIfAbsent(victim.getUUID(), k -> new HashSet<>()).add(name(p.getType()));
            }
        }
        return result;
    }

    @Override
    public String punish(String type, UUID victim, UUID operator, Duration duration, String reason) throws Exception {
        PunishmentType kind = type(type);
        if (kind == null) {
            return "unknown-action";
        }
        Optional<Punishment> done = api.getDrafter().draftBuilder()
                .type(kind)
                .victim(PlayerVictim.of(victim))
                .operator(PlayerOperator.of(operator))
                .reason(reason)
                .duration(kind == PunishmentType.KICK ? Duration.ZERO : duration)
                .build()
                .enactPunishment()
                .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        // Empty: a ban or mute is already active (LibertyBans allows only one of each at a time).
        return done.isPresent() ? null : "already-punished";
    }

    @Override
    public String undo(long id, String type) throws Exception {
        PunishmentType kind = type(type);
        if (kind == null || kind == PunishmentType.KICK) {
            return "unknown-action";
        }
        boolean undone = api.getRevoker().revokeByIdAndType(id, kind).undoPunishment()
                .toCompletableFuture().get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        return undone ? null : "not-active";
    }

    private static PunishmentType type(String type) {
        try {
            return PunishmentType.valueOf(type.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException | NullPointerException e) {
            return null;
        }
    }

    private static String name(PunishmentType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }
}
