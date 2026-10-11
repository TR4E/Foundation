package me.trae.foundation.database.api.tenant;

/**
 * Selects which tenant's rows a repository uses. For example, a repository for per-server settings can use
 * {@link #INSTANCE}, while one for data shared across servers can use {@link #GLOBAL}. The database connection is
 * configured separately.
 */
public enum TenantScope {

    /**
     * Example: an account table where each account id has one row and tenant separation is not needed.
     */
    NONE,

    /**
     * Example: a shared statistics table where every server reads and writes the same tenant's rows.
     */
    GLOBAL,

    /**
     * Example: settings shared by every server in one game mode, but separate from other game modes.
     */
    GROUP,

    /**
     * Example: configuration or state that belongs to one specific server instance.
     */
    INSTANCE
}