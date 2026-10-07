package me.trae.foundation.minecraft.velocity.plugin.framework.provider;

import com.velocitypowered.api.proxy.ProxyServer;
import lombok.AllArgsConstructor;
import me.trae.foundation.injector.api.annotation.Provider;
import me.trae.foundation.minecraft.velocity.plugin.framework.VelocityPlugin;

@AllArgsConstructor
public final class ProxyServerProvider {

    private final VelocityPlugin velocityPlugin;

    @Provider
    public ProxyServer proxyServer() {
        return this.velocityPlugin.getProxyServer();
    }
}