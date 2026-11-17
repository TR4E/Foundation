package me.trae.foundation.database.api.tier;

import java.util.EnumSet;
import java.util.Set;

public enum Tier {

    LOCAL, REDIS, POSTGRES;

    public static final Set<Tier> ALL = EnumSet.allOf(Tier.class);
}