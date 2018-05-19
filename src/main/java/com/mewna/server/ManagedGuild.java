package com.mewna.server;

import com.mewna.Geothermal;
import com.mewna.api.ApiContext;
import com.mewna.event.TrackEvent;
import com.mewna.jda.GeothermalVSU;
import com.mewna.jda.audio.PlayerHandle;
import com.mewna.nats.NatsServer;
import com.mewna.server.Playlist.QueuedTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.FunctionalResultHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import lombok.Getter;
import net.dv8tion.jda.Core;
import net.dv8tion.jda.manager.AudioManager;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static com.mewna.event.TrackEvent.Type.AUDIO_QUEUE_END;
import static com.mewna.event.TrackEvent.Type.AUDIO_TRACK_INVALID;
import static com.mewna.event.TrackEvent.Type.AUDIO_TRACK_QUEUE;

/**
 * @author amy
 * @since 1/21/18.
 */
@SuppressWarnings("unused")
public final class ManagedGuild {
    private static final Map<String, ManagedGuild> MANAGED_GUILDS = new ConcurrentHashMap<>();
    private static final Logger logger = LoggerFactory.getLogger(ManagedGuild.class);
    @Getter
    private final String guildId;
    @Getter
    private final PlayerHandle handle;
    private final ExecutorService pool = Executors.newCachedThreadPool();
    private final NatsServer queue;
    @Getter
    private final Playlist playlist;
    private final Geothermal geothermal;
    
    private ManagedGuild(final Geothermal geothermal, final String guildId, final PlayerHandle handle) {
        this.geothermal = geothermal;
        this.guildId = guildId;
        this.handle = handle;
        queue = geothermal.getNats();
        playlist = new Playlist(geothermal, guildId);
    }
    
    private static ManagedGuild create(final Geothermal geothermal, final String guildId) {
        final AudioPlayer audioPlayer = PlayerHandle.AUDIO_PLAYER_MANAGER.createPlayer();
        final PlayerHandle handle = new PlayerHandle(geothermal, guildId, audioPlayer);
        audioPlayer.addListener(handle);
        
        return new ManagedGuild(geothermal, guildId, handle);
    }
    
    public static ManagedGuild get(final Geothermal geothermal, final String guildId) {
        return MANAGED_GUILDS.computeIfAbsent(guildId, __ -> create(geothermal, guildId));
    }
    
    private static String getDomainName(final String url) throws URISyntaxException {
        try {
            final URI uri = new URI(url);
            return uri.getHost().replaceFirst("www.", "");
        } catch(final NullPointerException ignored) {
            throw new URISyntaxException("null", "invalid uri");
        }
    }
    
    public void openConnection(final Core core, final String session, final JSONObject vsu) {
        GeothermalVSU.acceptVSU(core, session, vsu);
    }
    
    public void closeConnection(final Core core) {
        handle.getAudioPlayer().stopTrack();
        getAudioManager(core).closeAudioConnection();
    }
    
    @SuppressWarnings("WeakerAccess")
    public AudioManager getAudioManager(final Core core) {
        return core.getAudioManager(guildId);
    }
    
    public void pauseTrack() {
        handle.getAudioPlayer().setPaused(!handle.getAudioPlayer().isPaused());
    }
    
    public void skipTracks(final ApiContext ctx, int toSkip) {
        if(toSkip == -1) {
            // Skip EVERYTHING
            playlist.deletePlaylist();
            queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, ctx, null));
        } else {
            // Skip only MOSTLY everything
            // Ensure sanity cap
            if(toSkip > 1000) {
                toSkip = 1000;
            }
            if(toSkip > 0) {
                playlist.skipAmount(toSkip);
                if(playlist.getLength() == 0) {
                    queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, ctx, null));
                } else {
                    startNextTrack(ctx);
                }
            }
        }
    }
    
    public void playTrack(final Core core, final ApiContext ctx, final String track, final PlayMode mode) {
        pool.execute(() -> {
            try {
                final String domainName = getDomainName(track);
                // Check if it's a YT track
                if(domainName.equalsIgnoreCase("youtube.com") || mode == PlayMode.FORCE_PLAY) {
                    // Valid track, do something
                    loadTrackFromURL(core, mode, ctx, track);
                } else {
                    // Invalid track
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null));
                }
            } catch(final URISyntaxException e) {
                // Not a valid URL, search YT
                loadTrackFromSearch(core, mode, ctx, track);
            }
        });
    }
    
    public void startNextTrack(final ApiContext context) {
        pool.execute(() -> {
            // TODO: Verify that we're actually connected before starting
            handle.getAudioPlayer().stopTrack();
            final QueuedTrack next = playlist.getNextTrack();
            if(next != null) {
                try {
                    final ApiContext ctx = next.getCtx();
                    final Core core = geothermal.getCoreManager().getCore(System.getenv("CLIENT_ID"), 0);
                    System.out.println("### TRACK FOUND, LOADING");
                    PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem(next.getUrl(), new FunctionalResultHandler(audioTrack -> {
                        logger.debug("AudioManager connected on guild {}: {}", ctx.getGuild(), core.getAudioManager(ctx.getGuild()).isConnected());
                        core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                        handle.getAudioPlayer().playTrack(audioTrack);
                    }, null,
                            () -> queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null)),
                            e -> queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null))));
                } catch(final Throwable t) {
                    t.printStackTrace();
                }
            } else {
                queue.queueTrackEvent(new TrackEvent(AUDIO_QUEUE_END, context, null));
            }
        });
    }
    
    private void loadTrackFromURL(final Core core, final PlayMode mode, final ApiContext ctx, final String track) {
        PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem(track, new FunctionalResultHandler(audioTrack -> {
            switch(mode) {
                case QUEUE:
                    playlist.queueTrack(new QueuedTrack(track, ctx, audioTrack.getInfo()));
                    queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, audioTrack.getInfo()));
                    break;
                case DIRECT_PLAY:
                    try {
                        final String domainName = getDomainName(track);
                        //noinspection StatementWithEmptyBody
                        if(!domainName.equalsIgnoreCase("youtube.com")) {
                            // TODO: Invalid track
                        } else {
                            // We're good
                            playlist.setCurrentTrack(new QueuedTrack(track, ctx, audioTrack.getInfo()));
                            core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                            handle.getAudioPlayer().playTrack(audioTrack);
                        }
                    } catch(final URISyntaxException e) {
                        e.printStackTrace();
                    }
                    break;
                case FORCE_PLAY:
                    playlist.setCurrentTrack(new QueuedTrack(track, ctx, audioTrack.getInfo()));
                    core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                    handle.getAudioPlayer().playTrack(audioTrack);
                    break;
            }
        }, pl -> {
            final List<AudioTrack> tracks = pl.getTracks();
            tracks.forEach(audioTrack -> playlist.queueTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx, audioTrack.getInfo())));
            
            // So this is kinda retarded, but:
            // Basically, because I REALLY don't wanna change the way that track events
            // are constructed, this goes full meme and crams info about how many tracks
            // were queued into an AudioTrackInfo instance
            //
            // Because #lazy
            //
            // Note that this only sets the "title" and "length" fields.
            queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, new AudioTrackInfo("Tracks Queued", null,
                    tracks.size(), null, false, null)));
        }, () -> {
            // Couldn't find a track, give up
        }, null));
    }
    
    private void loadTrackFromSearch(final Core core, final PlayMode mode, final ApiContext ctx, final String track) {
        final AtomicInteger counter = new AtomicInteger();
        PlayerHandle.AUDIO_PLAYER_MANAGER.loadItem("ytsearch:" + track, new FunctionalResultHandler(null, e -> {
            try {
                // Consume the playlist
                if(counter.get() != 0) {
                    return;
                }
                counter.set(1);
                // Queue
                final AudioTrack audioTrack = e.getTracks().get(0);
                switch(mode) {
                    case QUEUE:
                        playlist.queueTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx, audioTrack.getInfo()));
                        queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_QUEUE, ctx, audioTrack.getInfo()));
                        break;
                    case DIRECT_PLAY:
                        core.getAudioManager(ctx.getGuild()).setSendingHandler(handle);
                        handle.getAudioPlayer().playTrack(audioTrack);
                        playlist.setCurrentTrack(new QueuedTrack(audioTrack.getInfo().uri, ctx, audioTrack.getInfo()));
                        break;
                }
            } catch(final Throwable t) {
                t.printStackTrace();
            }
        }, () -> {
            // Couldn't find a track, give up
            queue.queueTrackEvent(new TrackEvent(AUDIO_TRACK_INVALID, ctx, null));
        }, null));
    }
    
    public enum PlayMode {
        QUEUE,
        DIRECT_PLAY,
        FORCE_PLAY,
    }
}
