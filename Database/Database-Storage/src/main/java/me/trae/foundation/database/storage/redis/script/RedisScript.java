package me.trae.foundation.database.storage.redis.script;

import io.lettuce.core.RedisNoScriptException;
import io.lettuce.core.ScriptOutputType;
import io.lettuce.core.api.sync.RedisCommands;
import lombok.RequiredArgsConstructor;
import me.trae.foundation.database.storage.driver.RedisDriver;

@RequiredArgsConstructor
public final class RedisScript {

    private final String source;

    private volatile String sha;

    public <Result> Result execute(final RedisDriver redisDriver, final ScriptOutputType scriptOutputType, final String[] keys, final String... arguments) {
        final RedisCommands<String, String> commands = redisDriver.getCommands();

        if (this.sha == null) {
            this.sha = commands.scriptLoad(this.source);
        }

        try {
            return commands.evalsha(this.sha, scriptOutputType, keys, arguments);
        } catch (final RedisNoScriptException exception) {
            this.sha = commands.scriptLoad(this.source);

            return commands.evalsha(this.sha, scriptOutputType, keys, arguments);
        }
    }
}