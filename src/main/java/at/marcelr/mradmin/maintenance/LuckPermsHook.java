package at.marcelr.mradmin.maintenance;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.util.Tristate;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * Permission check before the player is on the server (during the async pre-login). Only loaded when LuckPerms is
 * installed - this class must not be touched otherwise, it links against the LuckPerms API.
 */
public final class LuckPermsHook {

    private LuckPermsHook() {
    }

    /** @return TRUE / FALSE if LuckPerms has an explicit value, null if the permission is not set for the player */
    public static Boolean check(UUID uuid, String permission) throws Exception {
        LuckPerms api = LuckPermsProvider.get();
        User user = api.getUserManager().loadUser(uuid).get(10, TimeUnit.SECONDS);
        Tristate result = user.getCachedData().getPermissionData().checkPermission(permission);
        return result == Tristate.UNDEFINED ? null : result.asBoolean();
    }
}
