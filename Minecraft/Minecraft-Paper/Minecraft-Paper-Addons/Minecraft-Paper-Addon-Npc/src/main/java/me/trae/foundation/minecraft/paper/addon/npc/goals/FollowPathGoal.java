package me.trae.foundation.minecraft.paper.addon.npc.goals;

import com.destroystokyo.paper.entity.ai.Goal;
import com.destroystokyo.paper.entity.ai.GoalKey;
import com.destroystokyo.paper.entity.ai.GoalType;
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Mob;

import java.util.EnumSet;
import java.util.List;

@RequiredArgsConstructor
public final class FollowPathGoal implements Goal<Mob>, RestorableGoal<FollowPathGoal> {

    private static final GoalKey<Mob> GOAL_KEY = GoalKey.of(Mob.class, new NamespacedKey("custom", "follow_path"));

    private static final double ARRIVAL_DISTANCE_SQUARED = 1.0D;

    private final Mob mob;
    private final List<Location> locationList;
    private final boolean looping;
    private final double speed;
    private final long waitTicks;

    private int index;
    private long waited;
    private boolean finished;

    @Override
    public boolean shouldActivate() {
        if (this.finished) {
            return false;
        }

        if (this.locationList.isEmpty()) {
            return false;
        }

        return true;
    }

    @Override
    public boolean shouldStayActive() {
        if (this.finished) {
            return false;
        }

        if (this.locationList.isEmpty()) {
            return false;
        }

        if (this.index >= this.locationList.size()) {
            return false;
        }

        return true;
    }

    @Override
    public void start() {
        if (this.index >= this.locationList.size()) {
            this.index = 0;
        }

        this.moveToCurrent();
    }

    @Override
    public void stop() {
        this.mob.getPathfinder().stopPathfinding();
    }

    @Override
    public void tick() {
        final Location location = this.locationList.get(this.index);

        if (!location.getWorld().equals(this.mob.getWorld())) {
            this.advance();

            return;
        }

        if (location.distanceSquared(this.mob.getLocation()) > ARRIVAL_DISTANCE_SQUARED) {
            if (!this.mob.getPathfinder().hasPath()) {
                this.moveToCurrent();
            }

            return;
        }

        if (this.waited < this.waitTicks) {
            this.waited++;

            return;
        }

        this.advance();
    }

    private void advance() {
        this.waited = 0L;

        this.index++;

        if (this.index < this.locationList.size()) {
            this.moveToCurrent();
            return;
        }

        if (this.looping) {
            this.index = 0;

            this.moveToCurrent();
            return;
        }

        this.finished = true;
    }

    @Override
    public GoalKey<Mob> getKey() {
        return GOAL_KEY;
    }

    @Override
    public EnumSet<GoalType> getTypes() {
        return EnumSet.of(GoalType.MOVE, GoalType.JUMP);
    }

    @Override
    public void restore(final FollowPathGoal followPathGoal) {
        this.index = followPathGoal.index;
        this.waited = followPathGoal.waited;
        this.finished = followPathGoal.finished;
    }

    private void moveToCurrent() {
        this.mob.getPathfinder().moveTo(this.locationList.get(this.index), this.speed);
    }
}