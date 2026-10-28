package me.trae.foundation.minecraft.paper.addon.damage.enums;

public enum DeathMode {

    /**
     * Uses the most recent applied damage event for the death's killer, cause, item, and reason.
     */
    NORMAL,

    /**
     * Uses the most recent applied damage event dealt by a player, with or without a reason, for
     * the death's killer, cause, item, and reason, even if another source dealt the final damage.
     * Later player damage without a reason replaces any previously stored event with a reason.
     * Falls back to the most recent applied damage event when no player damage event is stored.
     */
    LAST_PLAYER_DAMAGER,

    /**
     * Uses the most recent applied damage event dealt by a player with a non-null reason for the
     * death's killer, cause, item, and reason. Later player damage without a reason does not replace
     * the stored event. Falls back to the most recent applied damage event when no qualifying event
     * is stored.
     */
    LAST_PLAYER_DAMAGER_WITH_REASON
}
