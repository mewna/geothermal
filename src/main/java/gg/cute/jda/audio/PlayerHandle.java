/*
 * This file is part of Hotspring.
 *
 * Hotspring is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Hotspring is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Hotspring.  If not, see <http://www.gnu.org/licenses/>.
 */

package gg.cute.jda.audio;

import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.http.HttpAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools;
import com.sedmelluq.discord.lavaplayer.tools.io.HttpClientTools.NoRedirectsStrategy;
import com.sedmelluq.discord.lavaplayer.tools.io.ThreadLocalHttpInterfaceManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import gg.cute.Geothermal;
import gg.cute.event.TrackEvent;
import gg.cute.nats.NatsServer;
import gg.cute.server.ManagedGuild;
import gg.cute.server.Playlist;
import gg.cute.server.Playlist.QueuedTrack;
import lombok.Getter;
import net.dv8tion.jda.audio.AudioSendHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.List;

import static gg.cute.event.TrackEvent.Type.AUDIO_TRACK_START;
import static gg.cute.event.TrackEvent.Type.AUDIO_TRACK_STOP;

/**
 * @author amy
 * @since 1/19/18.
 */
@SuppressWarnings("unchecked")
public class PlayerHandle extends AudioEventAdapter implements AudioSendHandler {
    public static final AudioPlayerManager AUDIO_PLAYER_MANAGER;
    private static final Logger logger = LoggerFactory.getLogger(PlayerHandle.class);
    
    static {
        AUDIO_PLAYER_MANAGER = new DefaultAudioPlayerManager();
        AUDIO_PLAYER_MANAGER.setPlayerCleanupThreshold(Long.MAX_VALUE);
        AudioSourceManagers.registerRemoteSources(AUDIO_PLAYER_MANAGER);
        try {
            final Field field = AUDIO_PLAYER_MANAGER.getClass().getDeclaredField("sourceManagers");
            field.setAccessible(true);
            @SuppressWarnings("TypeMayBeWeakened")
            final List<AudioSourceManager> sourceManagers = (List<AudioSourceManager>) field.get(AUDIO_PLAYER_MANAGER);
            for(final AudioSourceManager sourceManager : sourceManagers) {
                if(sourceManager instanceof HttpAudioSourceManager) {
                    final ThreadLocalHttpInterfaceManager tlhim =
                            new ThreadLocalHttpInterfaceManager(
                                    HttpClientTools.createSharedCookiesHttpBuilder()
                                            .setRedirectStrategy(new NoRedirectsStrategy())
                                            .setUserAgent("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/63.0.3239.132 Safari/537.36"),
                                    HttpClientTools.DEFAULT_REQUEST_CONFIG);
                    final Field him = sourceManager.getClass().getDeclaredField("httpInterfaceManager");
                    him.setAccessible(true);
                    final Field modifiersField = Field.class.getDeclaredField("modifiers");
                    modifiersField.setAccessible(true);
                    modifiersField.setInt(him, him.getModifiers() & ~Modifier.FINAL);
                    him.set(sourceManager, tlhim);
                    logger.info("Updated HIM!");
                    logger.info("Worked: " + him.get(sourceManager).equals(tlhim));
                }
            }
        } catch(final NoSuchFieldException | IllegalAccessException e) {
            e.printStackTrace();
        }
    }
    
    @Getter
    private final String guildId;
    @Getter
    private final AudioPlayer audioPlayer;
    private final NatsServer handle;
    private final Geothermal geothermal;
    private AudioFrame lastFrame;
    
    public PlayerHandle(final Geothermal geothermal, final String guildId, final AudioPlayer audioPlayer) {
        this.geothermal = geothermal;
        this.guildId = guildId;
        this.audioPlayer = audioPlayer;
        handle = geothermal.getNats();
    }
    
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }
    
    @Override
    public byte[] provide20MsAudio() {
        return lastFrame.data;
    }
    
    @Override
    public boolean isOpus() {
        return true;
    }
    
    @Override
    public void onTrackStart(final AudioPlayer player, final AudioTrack track) {
        if(player.isPaused()) {
            player.setPaused(false);
            //return;
        }
        logger.debug("Starting track: " + track.getInfo());
        final Playlist playlist = ManagedGuild.get(geothermal, guildId).getPlaylist();
        final QueuedTrack currentTrack = playlist.getCurrentTrack();
        if(currentTrack != null) {
            handle.queueTrackEvent(new TrackEvent(AUDIO_TRACK_START, currentTrack.getCtx(), track.getInfo()));
        }
    }
    
    @Override
    public void onTrackEnd(final AudioPlayer player, final AudioTrack track, final AudioTrackEndReason endReason) {
        logger.debug("Ending track: " + track.getInfo());
        logger.debug("End reason: " + endReason);
        final Playlist playlist = ManagedGuild.get(geothermal, guildId).getPlaylist();
        final QueuedTrack currentTrack = playlist.getCurrentTrack();
        if(currentTrack != null) {
            handle.queueTrackEvent(new TrackEvent(AUDIO_TRACK_STOP, currentTrack.getCtx(), track.getInfo()));
            // Start next track in the queue, if at all possible
            ManagedGuild.get(geothermal, guildId).startNextTrack(currentTrack.getCtx());
        }
    }
    
    @Override
    public void onTrackException(final AudioPlayer player, final AudioTrack track, final FriendlyException exception) {
        logger.warn("Track exception: " + player.getPlayingTrack().getInfo());
        exception.printStackTrace();
    }
    
    @Override
    public void onTrackStuck(final AudioPlayer player, final AudioTrack track, final long thresholdMs) {
        logger.warn("Track stuck: " + player.getPlayingTrack().getInfo());
    }
}
