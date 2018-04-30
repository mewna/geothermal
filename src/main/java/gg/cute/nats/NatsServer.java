package gg.cute.nats;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import gg.cute.Geothermal;
import gg.cute.event.TrackEvent;
import io.nats.client.Nats;
import io.nats.streaming.StreamingConnection;
import io.nats.streaming.StreamingConnectionFactory;
import io.nats.streaming.SubscriptionOptions;
import lombok.Getter;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * @author amy
 * @since 4/8/18.
 */
@SuppressWarnings({"unused", "WeakerAccess"})
public class NatsServer {
    private static final ObjectMapper MAPPER = new ObjectMapper();
    // TODO: Client ID needs to use container name; use Rancher metadata service
    private final StreamingConnectionFactory connectionFactory = new StreamingConnectionFactory("cute-nats", "cute-audio-server");
    private final Logger logger = LoggerFactory.getLogger(getClass());
    @SuppressWarnings({"FieldCanBeLocal", "unused"})
    private final Geothermal geothermal;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    @Getter
    private StreamingConnection connection;
    
    public NatsServer(final Geothermal geothermal) {
        this.geothermal = geothermal;
    }
    
    @SuppressWarnings("UnnecessarilyQualifiedInnerClassAccess")
    public void connect() {
        try {
            final String natsUrl = System.getenv("NATS_URL");
            logger.info("Connecting to NATS with: {}", natsUrl);
            connectionFactory.setNatsConnection(Nats.connect(natsUrl));
            connection = connectionFactory.createConnection();
            connection.subscribe("audio-event-queue", "audio-event-worker-queue", m -> {
                final String message = new String(m.getData());
                try {
                    logger.debug("Got socket message: {}", message);
                    
                    final JSONObject o = new JSONObject(message);
                    pool.execute(() -> geothermal.getEventHandler().handle(o));
                } catch(final Exception e) {
                    logger.error("Caught error while processing socket message:");
                    e.printStackTrace();
                }
            }, new SubscriptionOptions.Builder().durableName("cute-audio-event-queue-durable").build());
        } catch(final IOException | InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
    
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
    
    // Just kill me now ok? :(
    public void queueTrackEvent(final TrackEvent event) {
        pushBackendEvent(event.getT().name(), new JSONObject(toJson(event.getD())));
    }
    
    public <T> void pushBackendEvent(final String type, final T data) {
        pushEvent("backend-event-queue", type, data);
    }
    
    public <T> void pushShardEvent(final String type, final T data) {
        pushEvent("discord-event-queue", type, data);
    }
    
    public <T> void pushAudioEvent(final String type, final T data) {
        pushEvent("audio-event-queue", type, data);
    }
    
    public <T> void pushEvent(final String queue, final String type, final T data) {
        final JSONObject event = new JSONObject().put("t", type).put("ts", System.currentTimeMillis()).put("d", data);
        try {
            getConnection().publish(queue, event.toString().getBytes());
        } catch(final IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}