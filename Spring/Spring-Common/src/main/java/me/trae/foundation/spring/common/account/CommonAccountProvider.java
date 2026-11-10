package me.trae.foundation.spring.common.account;

import java.util.Optional;
import java.util.UUID;

public interface CommonAccountProvider<Account extends AccountIdentity<?>> {

    Optional<Account> getAccountById(final UUID id);
}