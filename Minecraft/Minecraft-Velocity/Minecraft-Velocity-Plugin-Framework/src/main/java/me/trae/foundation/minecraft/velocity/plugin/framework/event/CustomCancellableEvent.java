package me.trae.foundation.minecraft.velocity.plugin.framework.event;

import com.velocitypowered.api.event.ResultedEvent;
import lombok.Getter;

import java.util.Objects;

@Getter
public class CustomCancellableEvent extends CustomEvent implements ResultedEvent<ResultedEvent.GenericResult> {

    private GenericResult result = GenericResult.allowed();

    @Override
    public void setResult(final GenericResult result) {
        this.result = Objects.requireNonNull(result, "Result cannot be null");
    }
}