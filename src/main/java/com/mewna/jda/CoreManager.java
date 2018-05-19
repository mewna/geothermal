package com.mewna.jda;

import com.mewna.Geothermal;
import lombok.Getter;
import net.dv8tion.jda.Core;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author amy
 * @since 1/17/18.
 */
public class CoreManager {
    private final Map<Integer, Core> cores = new ConcurrentHashMap<>();
    
    @Getter
    private final Geothermal geothermal;
    
    public CoreManager(final Geothermal geothermal) {
        this.geothermal = geothermal;
    }
    
    public Core getCore(final String botId, final int shard) {
        return cores.computeIfAbsent(shard, (Integer s) -> new Core(botId, new GeothermalCoreClient()));
    }
}
