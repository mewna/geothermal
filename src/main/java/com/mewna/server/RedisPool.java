package com.mewna.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mewna.server.Playlist.QueuedTrack;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;
import redis.clients.jedis.Transaction;

import java.io.IOException;
import java.util.function.Consumer;

/**
 * @author amy
 * @since 4/19/18.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class RedisPool {
    private final JedisPool pool;
    @SuppressWarnings("FieldCanBeLocal")
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    private static final ObjectMapper MAPPER = new ObjectMapper();
    
    public RedisPool() {
        logger.info("Connecting to Redis...");
        final JedisPoolConfig config = new JedisPoolConfig();
        config.setMaxIdle(1500);
        config.setMaxTotal(1500);
        config.setMaxWaitMillis(500);
        pool = new JedisPool(config, System.getenv("REDIS_HOST"));
        logger.info("Redis connection pool ready!");
    }
    
    public void redis(final Consumer<Jedis> c) {
        try(Jedis jedis = pool.getResource()) {
            jedis.auth(System.getenv("REDIS_PASS"));
            c.accept(jedis);
        }
    }
    
    public void tredis(final Consumer<Transaction> t) {
        redis(c -> {
            final Transaction transaction = c.multi();
            t.accept(transaction);
            transaction.exec();
        });
    }
    
    // Playlist operations
    
    private <T> String toJson(final T o) {
        try {
            return MAPPER.writeValueAsString(o);
        } catch(final JsonProcessingException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    private <T> T fromJson(final String json, final Class<T> c) {
        try {
            return MAPPER.readValue(json, c);
        } catch(final IOException e) {
            throw new IllegalArgumentException(e);
        }
    }
    
    public <T> void queue(final String queue, final T data) {
        redis(c -> c.rpush(queue, toJson(data)));
    }
    
    public void delete(final String key) {
        redis(c -> c.del(key));
    }
    
    public int getQueueSize(final String queue) {
        final int[] l = {0};
        redis(c -> l[0] = Math.toIntExact(c.llen(queue)));
        return l[0];
    }
    
    public QueuedTrack deque(final String queue) {
        final QueuedTrack[] track = {null};
        redis(c -> {
            final String json = c.lpop(queue);
            if(json == null || json.isEmpty() || json.equalsIgnoreCase("{}")) {
                logger.warn("No more tracks for queue {}", queue);
            } else {
                track[0] = fromJson(json, QueuedTrack.class);
            }
        });
        logger.info("Loaded track: {}", track[0]);
        return track[0];
    }
}
