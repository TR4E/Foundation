package me.trae.foundation.database.api.holder;

/**
 * Selects whether a holder manages entities within one application instance or across several JVMs.
 */
public enum InstanceMode {

    /**
     * Example: an entity that is owned and cached by one JVM. Redis may still be enabled.
     */
    SINGLE_INSTANCE,

    /**
     * Example: a shared entity that may be accessed by several independent JVMs, with Redis enabled on its holder.
     */
    MULTI_INSTANCE
}