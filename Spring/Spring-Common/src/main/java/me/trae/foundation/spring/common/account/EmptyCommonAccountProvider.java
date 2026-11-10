package me.trae.foundation.spring.common.account;

import java.util.Optional;
import java.util.UUID;

public final class EmptyCommonAccountProvider implements CommonAccountProvider<AccountIdentity<?>> {

    @Override
    public Optional<AccountIdentity<?>> getAccountById(final UUID id) {
        return Optional.empty();
    }
}