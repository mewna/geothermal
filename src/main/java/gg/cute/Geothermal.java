package gg.cute;

import gg.cute.jda.CoreManager;
import gg.cute.nats.EventHandler;
import gg.cute.nats.NatsServer;
import gg.cute.server.RedisPool;
import lombok.Getter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author amy
 * @since 4/19/18.
 */
public final class Geothermal {
    @Getter
    private final NatsServer nats = new NatsServer(this);
    @Getter
    private final EventHandler eventHandler = new EventHandler(this);
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    @Getter
    private final CoreManager coreManager = new CoreManager(this);
    
    @Getter
    private final RedisPool redisPool = new RedisPool();
    
    private Geothermal() {
    }
    
    public static void main(final String[] args) {
        new Geothermal().start();
    }
    
    private void start() {
        logger.info("Starting up Geothermal...");
        logger.info("Connecting to NATS...");
        nats.connect();
        logger.info("Geothermal ready!");
    }
}
