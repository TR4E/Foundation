package me.trae.foundation.minecraft.paper.plugin.framework.stash;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import me.trae.foundation.minecraft.paper.plugin.framework.utility.UtilServer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.title.Title;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Getter
public final class TitleStash {

    private final Component title, subTitle;
    private final long fadeInDuration, stayDuration, fadeOutDuration;

    public static TitleStash of(final Component title, final Component subTitle, final long fadeInDuration, final long stayDuration, final long fadeOutDuration) {
        return new TitleStash(Objects.requireNonNullElse(title, Component.empty()), Objects.requireNonNullElse(subTitle, Component.empty()), fadeInDuration, stayDuration, fadeOutDuration);
    }

    public static TitleStash of(final Component title, final Component subTitle, final long stayDuration) {
        return of(title, subTitle, 1000L, stayDuration, 1000L);
    }

    public void send(final List<Player> playerList) {
        final Title title = this.build();

        for (final Player player : playerList) {
            player.showTitle(title);
        }
    }

    public void send(final Player player) {
        this.send(Collections.singletonList(player));
    }

    public void broadcast() {
        this.send(UtilServer.getOnlinePlayers());
    }

    private Title build() {
        return Title.title(this.title, this.subTitle, Title.Times.times(Duration.ofMillis(this.fadeInDuration), Duration.ofMillis(this.stayDuration), Duration.ofMillis(this.fadeOutDuration)));
    }
}