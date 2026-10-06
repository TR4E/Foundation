package me.trae.foundation.minecraft.paper.plugin.framework.event;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.bukkit.event.Cancellable;

@NoArgsConstructor
@Getter
public class CustomCancellableEvent extends CustomEvent implements Cancellable {

    @Setter
    private boolean cancelled;

    private String cancelledReason;

    public CustomCancellableEvent(final boolean isAsync) {
        super(isAsync);
    }

    public void setCancelledWithReason(final String cancelledReason) {
        this.cancelledReason = cancelledReason;
        this.setCancelled(true);
    }
}