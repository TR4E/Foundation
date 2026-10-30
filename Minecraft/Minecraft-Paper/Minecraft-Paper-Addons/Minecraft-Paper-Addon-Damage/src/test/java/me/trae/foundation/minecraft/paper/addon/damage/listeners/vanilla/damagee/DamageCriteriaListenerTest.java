package me.trae.foundation.minecraft.paper.addon.damage.listeners.vanilla.damagee;

import me.trae.foundation.minecraft.paper.addon.damage.events.CustomDamageEvent;
import me.trae.foundation.minecraft.paper.addon.damage.events.DamageAppliedEvent;
import me.trae.foundation.minecraft.paper.addon.damage.testing.DamageTestSupport;
import net.minecraft.advancements.CriteriaTriggers;
import net.minecraft.advancements.CriterionTrigger;
import net.minecraft.advancements.criterion.EntityHurtPlayerTrigger;
import net.minecraft.advancements.criterion.EntityPredicate;
import net.minecraft.advancements.criterion.PlayerHurtEntityTrigger;
import net.minecraft.server.PlayerAdvancements;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.level.storage.loot.LootContext;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SuppressWarnings("unchecked")
@org.junit.jupiter.api.Tag("VERIFIED")
class DamageCriteriaListenerTest extends DamageTestSupport {

    @Test
    void awardsShieldStatisticAndBothCriteriaExactlyOnceWithOriginalAndFinalDamage() throws Exception {
        final ServerLevel serverLevel = mock(ServerLevel.class);
        final ServerPlayer victimServerPlayer = mock(ServerPlayer.class);
        final ServerPlayer attackerServerPlayer = mock(ServerPlayer.class);
        final DamageSource damageSource = mock(DamageSource.class);
        final EntityHurtPlayerTrigger.TriggerInstance entityHurtPlayerTriggerInstance = mock(EntityHurtPlayerTrigger.TriggerInstance.class);
        final PlayerHurtEntityTrigger.TriggerInstance playerHurtEntityTriggerInstance = mock(PlayerHurtEntityTrigger.TriggerInstance.class);
        final CriterionTrigger.Listener<EntityHurtPlayerTrigger.TriggerInstance> victimListener = mock(CriterionTrigger.Listener.class);
        when(victimListener.trigger()).thenReturn(entityHurtPlayerTriggerInstance);
        final CriterionTrigger.Listener<PlayerHurtEntityTrigger.TriggerInstance> attackerListener = mock(CriterionTrigger.Listener.class);
        when(attackerListener.trigger()).thenReturn(playerHurtEntityTriggerInstance);
        final PlayerAdvancements victimAdvancementsPlayerAdvancements = mock(PlayerAdvancements.class);
        final PlayerAdvancements attackerAdvancementsPlayerAdvancements = mock(PlayerAdvancements.class);
        final java.lang.reflect.Field field = PlayerAdvancements.class.getDeclaredField("criterionData");
        field.setAccessible(true);
        field.set(victimAdvancementsPlayerAdvancements, Map.of(CriteriaTriggers.ENTITY_HURT_PLAYER, Set.of(victimListener)));
        field.set(attackerAdvancementsPlayerAdvancements, Map.of(CriteriaTriggers.PLAYER_HURT_ENTITY, Set.of(attackerListener)));
        when(victimServerPlayer.getAdvancements()).thenReturn(victimAdvancementsPlayerAdvancements);
        when(attackerServerPlayer.getAdvancements()).thenReturn(attackerAdvancementsPlayerAdvancements);
        final CustomDamageEvent customDamageEvent = damage(
                player(victimServerPlayer, serverLevel),
                player(attackerServerPlayer, serverLevel),
                source(damageSource),
                DamageCause.ENTITY_ATTACK,
                12.0D
        );
        customDamageEvent.setDamage(5.0D);
        customDamageEvent.setBlockedDamage(3.0D);
        final LootContext lootContext = mock(LootContext.class);

        try (final MockedStatic<EntityPredicate> entityPredicateMockedStatic = mockStatic(EntityPredicate.class)) {
            entityPredicateMockedStatic.when(() -> EntityPredicate.createContext(attackerServerPlayer, victimServerPlayer)).thenReturn(lootContext);
            new DamageCriteriaListener().onDamageApplied(new DamageAppliedEvent(customDamageEvent));
        }

        verify(entityHurtPlayerTriggerInstance).matches(victimServerPlayer, damageSource, 12.0F, 5.0F, true);
        verify(playerHurtEntityTriggerInstance).matches(attackerServerPlayer, lootContext, damageSource, 12.0F, 5.0F, true);
        verify(victimServerPlayer).awardStat(Stats.DAMAGE_BLOCKED_BY_SHIELD, 30);
    }

    @Test
    void statisticsAndCriteriaHaveExactlyOneProductionOwner() throws Exception {
        final Map<String, String> expectedOwnerMap = Map.of(
                "Stats.DAMAGE_TAKEN",
                "HealthApplicationListener.java",
                "Stats.DAMAGE_DEALT",
                "AttackEffectsListener.java",
                "Stats.DAMAGE_RESISTED",
                "ReductionStatisticListener.java",
                "Stats.DAMAGE_DEALT_RESISTED",
                "ReductionStatisticListener.java",
                "Stats.DAMAGE_ABSORBED",
                "ReductionStatisticListener.java",
                "Stats.DAMAGE_DEALT_ABSORBED",
                "ReductionStatisticListener.java",
                "Stats.DAMAGE_BLOCKED_BY_SHIELD",
                "DamageCriteriaListener.java",
                "CriteriaTriggers.ENTITY_HURT_PLAYER",
                "DamageCriteriaListener.java",
                "CriteriaTriggers.PLAYER_HURT_ENTITY",
                "DamageCriteriaListener.java"
        );
        final Map<String, Set<String>> actualOwnerMap = new HashMap<>();

        try (final var pathStream = Files.walk(Path.of("src/main/java/me/trae/foundation/minecraft/paper/addon/damage"))) {
            for (final Path path : pathStream.filter((final var candidate) -> candidate.toString().endsWith(".java")).toList()) {
                final String source = Files.readString(path);

                for (final String symbol : expectedOwnerMap.keySet()) {
                    if (java.util.regex.Pattern.compile("%s\\b".formatted(java.util.regex.Pattern.quote(symbol))).matcher(source).find()) {
                        actualOwnerMap.computeIfAbsent(symbol, _ -> new HashSet<>()).add(path.getFileName().toString());
                    }
                }
            }
        }

        expectedOwnerMap.forEach((final var symbol, final var owner) -> assertEquals(Set.of(owner), actualOwnerMap.get(symbol), symbol));
    }
}