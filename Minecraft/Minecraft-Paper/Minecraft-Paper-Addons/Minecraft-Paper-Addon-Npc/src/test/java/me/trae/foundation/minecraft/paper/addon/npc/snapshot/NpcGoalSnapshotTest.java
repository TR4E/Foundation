package me.trae.foundation.minecraft.paper.addon.npc.snapshot;

import com.destroystokyo.paper.entity.ai.Goal;
import me.trae.foundation.minecraft.paper.addon.npc.NpcTestSupport;
import me.trae.foundation.minecraft.paper.addon.npc.goals.FollowPathGoal;
import me.trae.foundation.minecraft.paper.addon.npc.goals.LookAtEntityGoal;
import me.trae.foundation.minecraft.paper.addon.npc.goals.RestorableGoal;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.withSettings;

final class NpcGoalSnapshotTest extends NpcTestSupport {

    @Test
    void restoresAPathGoalIntoAnExistingGoalOfTheSameClass() throws ReflectiveOperationException {
        final Mob originalMob = this.entity(Mob.class);
        final Mob restoredMob = this.entity(Mob.class);
        final FollowPathGoal originalFollowPathGoal = new FollowPathGoal(originalMob, List.of(), false, 1.0D, 2L);
        final FollowPathGoal restoredFollowPathGoal = new FollowPathGoal(restoredMob, List.of(), false, 1.0D, 2L);
        this.setField(originalFollowPathGoal, "index", 2);
        this.setField(originalFollowPathGoal, "waited", 1L);
        this.setField(originalFollowPathGoal, "finished", true);
        when(this.mobGoals.getAllGoals(originalMob)).thenReturn(List.of(originalFollowPathGoal));
        when(this.mobGoals.getGoal(restoredMob, originalFollowPathGoal.getKey())).thenReturn(restoredFollowPathGoal);
        final NpcGoalSnapshot npcGoalSnapshot = new NpcGoalSnapshot(originalMob);
        npcGoalSnapshot.apply(restoredMob);

        assertEquals(2, this.<Integer>field(restoredFollowPathGoal, FollowPathGoal.class, "index"));
        assertEquals(1L, this.<Long>field(restoredFollowPathGoal, FollowPathGoal.class, "waited"));
        assertFalse(restoredFollowPathGoal.shouldActivate());
    }

    @Test
    @SuppressWarnings({"rawtypes", "unchecked"})
    void theSameGoalKeyDoesNotPermitRestorationIntoADifferentClass() {
        final Mob originalMob = this.entity(Mob.class);
        final Mob restoredMob = this.entity(Mob.class);
        final FollowPathGoal followPathGoal = new FollowPathGoal(originalMob, List.of(), false, 1.0D, 0L);
        final Goal<Mob> otherGoal = mock(Goal.class, withSettings().extraInterfaces(RestorableGoal.class));
        when(otherGoal.getKey()).thenReturn(followPathGoal.getKey());
        when(this.mobGoals.getAllGoals(originalMob)).thenReturn(List.of(followPathGoal));
        when(this.mobGoals.getGoal(restoredMob, followPathGoal.getKey())).thenReturn(otherGoal);
        new NpcGoalSnapshot(originalMob).apply(restoredMob);

        verify((RestorableGoal) otherGoal, never()).restore(any());
    }

    @Test
    void lookAtGoalsAreNotCapturedOrRestored() throws ReflectiveOperationException {
        final Mob originalMob = this.entity(Mob.class);
        final Mob restoredMob = this.entity(Mob.class);
        final LookAtEntityGoal originalLookAtEntityGoal = new LookAtEntityGoal(
                LivingEntity.class, originalMob, 8.0D, 1.0F, false
        );
        this.setField(originalLookAtEntityGoal, "ticks", 60);
        final LookAtEntityGoal restoredLookAtEntityGoal = new LookAtEntityGoal(
                LivingEntity.class, restoredMob, 8.0D, 1.0F, false
        );
        when(this.mobGoals.getAllGoals(originalMob)).thenReturn(List.of(originalLookAtEntityGoal));
        new NpcGoalSnapshot(originalMob).apply(restoredMob);

        verify(this.mobGoals, never()).getGoal(eq(restoredMob), any());
        assertEquals(0, this.<Integer>field(restoredLookAtEntityGoal, LookAtEntityGoal.class, "ticks"));
        assertNull(this.field(restoredLookAtEntityGoal, LookAtEntityGoal.class, "target"));
    }

    @Test
    void missingOrNonRestorableCurrentGoalsAreSkipped() {
        final Mob originalMob = this.entity(Mob.class);
        final Mob restoredMob = this.entity(Mob.class);
        final FollowPathGoal followPathGoal = new FollowPathGoal(originalMob, List.of(), false, 1.0D, 0L);
        when(this.mobGoals.getAllGoals(originalMob)).thenReturn(List.of(followPathGoal));
        final NpcGoalSnapshot npcGoalSnapshot = new NpcGoalSnapshot(originalMob);
        assertDoesNotThrow(() -> npcGoalSnapshot.apply(restoredMob));

        final LookAtEntityGoal lookAtEntityGoal = new LookAtEntityGoal(LivingEntity.class, restoredMob, 8.0D, 1.0F, false);
        when(this.mobGoals.getGoal(restoredMob, followPathGoal.getKey())).thenReturn(lookAtEntityGoal);
        assertDoesNotThrow(() -> npcGoalSnapshot.apply(restoredMob));
    }

    @Test
    void nonMobEntitiesHaveNoGoalState() {
        final NpcGoalSnapshot npcGoalSnapshot = new NpcGoalSnapshot(this.livingEntity());
        npcGoalSnapshot.apply(this.livingEntity());

        verifyNoInteractions(this.mobGoals);
    }
}
