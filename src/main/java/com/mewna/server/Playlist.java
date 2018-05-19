package com.mewna.server;

import com.mewna.Geothermal;
import com.mewna.api.ApiContext;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import lombok.*;

/**
 * @author amy
 * @since 1/21/18.
 */
@RequiredArgsConstructor
public class Playlist {
    private static final String PLAYLIST_QUEUE = "%s:playlist-queue";
    @Getter
    private final Geothermal handle;
    @Getter
    private final String guildId;
    @Getter
    @Setter
    private QueuedTrack currentTrack;
    
    private String getQueueName() {
        return String.format(PLAYLIST_QUEUE, guildId);
    }
    
    public void queueTrack(final QueuedTrack track) {
        handle.getRedisPool().queue(getQueueName(), track);
    }
    
    public int getLength() {
        return handle.getRedisPool().getQueueSize(getQueueName());
    }
    
    public QueuedTrack getNextTrack() {
        final QueuedTrack nextTrack = handle.getRedisPool().deque(getQueueName());
        currentTrack = nextTrack;
        return nextTrack;
    }
    
    public void skipAmount(int amount) {
        while(amount > 0) {
            getNextTrack();
            --amount;
        }
    }
    
    public void deletePlaylist() {
        handle.getRedisPool().delete(String.format(PLAYLIST_QUEUE, guildId));
    }
    
    @Value
    @ToString
    public static final class QueuedTrack {
        private final String url;
        private final ApiContext ctx;
        private final AudioTrackInfo trackInfo;
    }
}
