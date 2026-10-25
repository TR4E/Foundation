package me.trae.foundation.minecraft.paper.addon.npc;

import com.destroystokyo.paper.entity.Pathfinder;
import me.trae.foundation.minecraft.paper.addon.npc.goals.FollowPathGoal;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Mob;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyDouble;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

final class FollowPathGoalTest extends NpcTestSupport {

    @Test
    void restoreCopiesIndexWaitedAndFinished() throws ReflectiveOperationException {
        final FollowPathGoal originalFollowPathGoal = this.followPathGoal(List.of(this.location(0.0D, 0.0D)), false, 0L);
        this.setField(originalFollowPathGoal, "index", 3);
        this.setField(originalFollowPathGoal, "waited", 7L);
        this.setField(originalFollowPathGoal, "finished", true);
        final FollowPathGoal restoredFollowPathGoal = this.followPathGoal(List.of(this.location(0.0D, 0.0D)), true, 20L);
        restoredFollowPathGoal.restore(originalFollowPathGoal);

        assertEquals(3, this.<Integer>field(restoredFollowPathGoal, FollowPathGoal.class, "index"));
        assertEquals(7L, this.<Long>field(restoredFollowPathGoal, FollowPathGoal.class, "waited"));
        assertFalse(restoredFollowPathGoal.shouldActivate());
        assertFalse(restoredFollowPathGoal.shouldStayActive());
    }

    @Test
    void arrivalAtExactlyOneBlockAdvancesToTheNextWaypoint() throws ReflectiveOperationException {
        final Mob mob = this.mobAt(this.location(1.0D, 0.0D));
        final Location nextLocation = this.location(10.0D, 0.0D);
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                mob, List.of(this.location(0.0D, 0.0D), nextLocation), false, 1.25D, 0L
        );
        followPathGoal.tick();

        assertEquals(1, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        verify(mob.getPathfinder()).moveTo(nextLocation, 1.25D);
    }

    @Test
    void arrivalInsideOneBlockAdvances() {
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                this.mobAt(this.location(0.5D, 0.5D)),
                List.of(this.location(0.0D, 0.0D)),
                false,
                1.0D,
                0L
        );
        followPathGoal.tick();

        assertFalse(followPathGoal.shouldActivate());
    }

    @Test
    void outsideArrivalDistanceRetriesPathfindingWithoutAdvancing() throws ReflectiveOperationException {
        final Mob mob = this.mobAt(this.location(1.01D, 0.0D));
        final Location waypointLocation = this.location(0.0D, 0.0D);
        final FollowPathGoal followPathGoal = new FollowPathGoal(mob, List.of(waypointLocation), false, 1.0D, 0L);
        followPathGoal.tick();

        assertEquals(0, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertTrue(followPathGoal.shouldActivate());
        verify(mob.getPathfinder()).moveTo(waypointLocation, 1.0D);
    }

    @Test
    void anExistingPathIsNotReissuedWhileApproaching() {
        final Mob mob = this.mobAt(this.location(10.0D, 0.0D));
        when(mob.getPathfinder().hasPath()).thenReturn(true);
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                mob, List.of(this.location(0.0D, 0.0D)), false, 1.0D, 0L
        );
        followPathGoal.tick();

        verify(mob.getPathfinder(), never()).moveTo(any(Location.class), anyDouble());
    }

    @Test
    void waitsTheConfiguredTicksAtEveryWaypointAndResetsTheWaitCounter() throws ReflectiveOperationException {
        final Mob mob = this.mobAt(this.location(0.0D, 0.0D));
        final Location firstLocation = this.location(0.0D, 0.0D);
        final Location secondLocation = this.location(10.0D, 0.0D);
        final FollowPathGoal followPathGoal = new FollowPathGoal(mob, List.of(firstLocation, secondLocation), false, 1.0D, 2L);
        IntStream.range(0, 2).forEach(_ -> followPathGoal.tick());
        assertEquals(0, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(2L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));

        followPathGoal.tick();
        assertEquals(1, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));

        when(mob.getLocation()).thenReturn(secondLocation);
        IntStream.range(0, 2).forEach(_ -> followPathGoal.tick());
        assertTrue(followPathGoal.shouldStayActive());
        followPathGoal.tick();
        assertFalse(followPathGoal.shouldActivate());
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));
    }

    @Test
    void loopingWrapsToTheFirstWaypoint() throws ReflectiveOperationException {
        final Mob mob = this.mobAt(this.location(0.0D, 0.0D));
        final Location firstLocation = this.location(0.0D, 0.0D);
        final Location secondLocation = this.location(10.0D, 0.0D);
        final FollowPathGoal followPathGoal = new FollowPathGoal(mob, List.of(firstLocation, secondLocation), true, 1.0D, 0L);
        followPathGoal.tick();
        when(mob.getLocation()).thenReturn(secondLocation);
        followPathGoal.tick();

        assertEquals(0, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertTrue(followPathGoal.shouldActivate());
        assertTrue(followPathGoal.shouldStayActive());
        verify(mob.getPathfinder()).moveTo(firstLocation, 1.0D);
    }

    @Test
    void nonLoopingCompletionNeverReactivatesEvenAfterStopAndStart() {
        final FollowPathGoal followPathGoal = this.followPathGoal(List.of(this.location(0.0D, 0.0D)), false, 0L);
        followPathGoal.start();
        followPathGoal.tick();
        assertFalse(followPathGoal.shouldActivate());
        assertFalse(followPathGoal.shouldStayActive());

        followPathGoal.stop();
        followPathGoal.start();
        assertFalse(followPathGoal.shouldActivate());
        assertFalse(followPathGoal.shouldStayActive());
    }

    @Test
    void startRetainsRestoredIndexAndWaitProgress() throws ReflectiveOperationException {
        final List<Location> locationList = List.of(this.location(0.0D, 0.0D), this.location(10.0D, 0.0D));
        final FollowPathGoal originalFollowPathGoal = this.followPathGoal(locationList, false, 5L);
        this.setField(originalFollowPathGoal, "index", 1);
        this.setField(originalFollowPathGoal, "waited", 3L);
        final Mob mob = this.mobAt(locationList.getLast());
        final FollowPathGoal restoredFollowPathGoal = new FollowPathGoal(mob, locationList, false, 1.0D, 5L);
        restoredFollowPathGoal.restore(originalFollowPathGoal);
        restoredFollowPathGoal.start();

        assertEquals(1, this.<Integer>field(restoredFollowPathGoal, FollowPathGoal.class, "index"));
        assertEquals(3L, this.<Long>field(restoredFollowPathGoal, FollowPathGoal.class, "waited"));
        verify(mob.getPathfinder()).moveTo(locationList.getLast(), 1.0D);
    }

    @Test
    void startClampsARestoredIndexAfterThePathIsShortened() throws ReflectiveOperationException {
        final List<Location> locationList = new ArrayList<>(List.of(
                this.location(0.0D, 0.0D), this.location(10.0D, 0.0D), this.location(20.0D, 0.0D)
        ));
        final FollowPathGoal originalFollowPathGoal = this.followPathGoal(locationList, false, 0L);
        this.setField(originalFollowPathGoal, "index", 2);
        locationList.removeLast();
        locationList.removeLast();
        final Mob mob = this.mobAt(locationList.getFirst());
        final FollowPathGoal restoredFollowPathGoal = new FollowPathGoal(mob, locationList, false, 1.0D, 0L);
        restoredFollowPathGoal.restore(originalFollowPathGoal);
        restoredFollowPathGoal.start();

        assertEquals(0, this.<Integer>field(restoredFollowPathGoal, FollowPathGoal.class, "index"));
        verify(mob.getPathfinder()).moveTo(locationList.getFirst(), 1.0D);
    }

    @Test
    void emptyPathsDoNotActivateOrStayActive() {
        final FollowPathGoal followPathGoal = this.followPathGoal(List.of(), true, 0L);

        assertFalse(followPathGoal.shouldActivate());
        assertFalse(followPathGoal.shouldStayActive());
    }

    @Test
    void aWaypointInAnotherWorldIsSkippedImmediatelyWithoutWaiting() throws ReflectiveOperationException {
        final World otherWorld = mock(World.class);
        final Mob mob = this.mobAt(new Location(otherWorld, 0.0D, 64.0D, 0.0D));
        when(mob.getWorld()).thenReturn(otherWorld);
        final Location nextLocation = new Location(otherWorld, 10.0D, 64.0D, 0.0D);
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                mob,
                List.of(this.location(0.0D, 0.0D), nextLocation),
                false,
                1.0D,
                20L
        );
        followPathGoal.tick();

        assertEquals(1, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));
        assertTrue(followPathGoal.shouldActivate());
        verify(mob.getPathfinder()).moveTo(nextLocation, 1.0D);
    }

    @Test
    void anAllCrossWorldLoopSkipsExactlyOneWaypointPerTick() throws ReflectiveOperationException {
        final World otherWorld = mock(World.class);
        final Location firstLocation = new Location(otherWorld, 0.0D, 64.0D, 0.0D);
        final Location secondLocation = new Location(otherWorld, 10.0D, 64.0D, 0.0D);
        final Mob mob = this.mobAt(this.location(0.0D, 0.0D));
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                mob,
                List.of(firstLocation, secondLocation),
                true,
                1.0D,
                20L
        );
        this.setField(followPathGoal, "waited", 7L);
        followPathGoal.tick();

        assertEquals(1, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));
        verify(mob.getPathfinder()).moveTo(secondLocation, 1.0D);

        followPathGoal.tick();

        assertEquals(0, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));
        assertTrue(followPathGoal.shouldActivate());
        verify(mob.getPathfinder()).moveTo(firstLocation, 1.0D);
        verify(mob.getPathfinder(), times(2)).moveTo(any(Location.class), anyDouble());
    }

    @Test
    void skippingTheLastCrossWorldWaypointFinishesANonLoopingRoute() throws ReflectiveOperationException {
        final World otherWorld = mock(World.class);
        final FollowPathGoal followPathGoal = new FollowPathGoal(
                this.mobAt(this.location(0.0D, 0.0D)),
                List.of(new Location(otherWorld, 0.0D, 64.0D, 0.0D)),
                false,
                1.0D,
                20L
        );
        followPathGoal.tick();

        assertEquals(1, this.<Integer>field(followPathGoal, FollowPathGoal.class, "index"));
        assertEquals(0L, this.<Long>field(followPathGoal, FollowPathGoal.class, "waited"));
        assertFalse(followPathGoal.shouldActivate());
        assertFalse(followPathGoal.shouldStayActive());
    }

    private FollowPathGoal followPathGoal(final List<Location> locationList, final boolean looping, final long waitTicks) {
        return new FollowPathGoal(this.mobAt(this.location(0.0D, 0.0D)), locationList, looping, 1.0D, waitTicks);
    }

    private Mob mobAt(final Location location) {
        final Mob mob = this.entity(Mob.class);
        when(mob.getLocation()).thenReturn(location);
        when(mob.getPathfinder()).thenReturn(mock(Pathfinder.class));
        return mob;
    }
}
