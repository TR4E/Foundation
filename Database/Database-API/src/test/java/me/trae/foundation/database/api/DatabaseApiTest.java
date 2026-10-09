package me.trae.foundation.database.api;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import me.trae.foundation.database.api.entity.Entity;
import me.trae.foundation.database.api.exception.SchemaException;
import me.trae.foundation.database.api.property.EntityProperty;
import me.trae.foundation.database.api.property.EntityPropertyRegistry;
import me.trae.foundation.database.api.query.Direction;
import me.trae.foundation.database.api.query.Operator;
import me.trae.foundation.database.api.query.Query;
import me.trae.foundation.database.api.query.TenantSelection;
import me.trae.foundation.database.api.tenant.Tenant;
import me.trae.foundation.database.api.tenant.TenantScope;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class DatabaseApiTest {

    private static final EntityProperty<Profile, String> NAME = EntityProperty.register(Profile.class, "name", Profile::getName, Profile::setName, String.class, true);
    private static final EntityProperty<Profile, Integer> LEVEL = EntityProperty.register(Profile.class, "level", Profile::getLevel, Profile::setLevel, Integer.class, false);
    private static final EntityProperty<StaffProfile, String> ROLE = EntityProperty.register(StaffProfile.class, "role", StaffProfile::getRole, StaffProfile::setRole, String.class, true);

    @Test
    void resolvesTenantIdPerScope() {
        final Tenant tenant = new Tenant("lobby", 1);

        assertEquals(Optional.empty(), tenant.resolve(TenantScope.NONE));
        assertEquals(Optional.of(Tenant.GLOBAL), tenant.resolve(TenantScope.GLOBAL));
        assertEquals(Optional.of("lobby"), tenant.resolve(TenantScope.GROUP));
        assertEquals(Optional.of("lobby-1"), tenant.resolve(TenantScope.INSTANCE));
    }

    @Test
    void propertiesReadAndWriteThroughAccessors() {
        final Profile profile = new Profile(UUID.randomUUID());

        NAME.setValue(profile, "Trae");

        assertEquals("Trae", NAME.getValue(profile));
        assertTrue(NAME.isPersistent());
        assertFalse(LEVEL.isPersistent());
    }

    @Test
    void rejectsDuplicateAndPrimitiveProperties() {
        assertThrows(SchemaException.class, () -> EntityProperty.register(Profile.class, "name", Profile::getName, Profile::setName, String.class, true));
        assertThrows(SchemaException.class, () -> EntityProperty.register(Profile.class, "score", Profile::getLevel, Profile::setLevel, int.class, true));
    }

    @Test
    void subclassesIncludeSuperclassProperties() {
        assertEquals(List.of(ROLE.getName(), NAME.getName(), LEVEL.getName()), EntityPropertyRegistry.getProperties(StaffProfile.class).stream().map(EntityProperty::getName).toList());
    }

    @Test
    void queryCollectsConditionsOrdersAndTenant() {
        final Query<Profile> query = Query.where(NAME, Operator.EQUALS, "Trae").and(LEVEL, Operator.GREATER_THAN, 5).orderBy(LEVEL, Direction.DESCENDING).limit(10).offset(20);

        assertEquals(2, query.getConditionList().size());
        assertSame(LEVEL, query.getConditionList().get(1).getEntityProperty());
        assertEquals(5, query.getConditionList().get(1).getValue());
        assertEquals(Direction.DESCENDING, query.getOrderList().getFirst().getDirection());
        assertEquals(10, query.getLimit());
        assertEquals(20, query.getOffset());
        assertEquals(TenantSelection.OWN, query.getTenantSelection());

        query.tenant("lobby-1");
        assertEquals(TenantSelection.SPECIFIC, query.getTenantSelection());
        assertEquals("lobby-1", query.getTenantId());

        query.allTenants();
        assertEquals(TenantSelection.ALL, query.getTenantSelection());
        assertNull(query.getTenantId());
    }

    @RequiredArgsConstructor
    @Getter
    @Setter
    private static class Profile implements Entity {

        private final UUID id;
        private String name;
        private Integer level;
    }

    @Getter
    @Setter
    private static final class StaffProfile extends Profile {

        private String role;

        private StaffProfile(final UUID id) {
            super(id);
        }
    }
}