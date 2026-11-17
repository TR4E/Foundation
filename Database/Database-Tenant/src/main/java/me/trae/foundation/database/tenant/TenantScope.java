package me.trae.foundation.database.tenant;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Optional;
import java.util.function.Supplier;

@AllArgsConstructor
@Getter
public final class TenantScope {

    private static final ScopedValue<Tenant> CURRENT = ScopedValue.newInstance();

    private final Tenant defaultTenant;

    public Optional<Tenant> getTenant() {
        return CURRENT.isBound() ? Optional.of(CURRENT.get()) : Optional.ofNullable(this.defaultTenant);
    }

    public <Value> Value call(final Tenant tenant, final Supplier<Value> supplier) {
        return ScopedValue.where(CURRENT, tenant).call(supplier::get);
    }

    public void run(final Tenant tenant, final Runnable runnable) {
        this.call(tenant, () -> {
            runnable.run();

            return null;
        });
    }
}