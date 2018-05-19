package com.mewna.nats;

import com.mewna.Geothermal;
import com.mewna.api.ApiContext;
import com.mewna.event.TrackEvent;
import com.mewna.event.TrackEvent.Type;
import com.mewna.server.ManagedGuild;
import com.mewna.server.ManagedGuild.PlayMode;
import com.mewna.server.Playlist.QueuedTrack;
import net.dv8tion.jda.Core;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author amy
 * @since 4/19/18.
 */
public class EventHandler {
    private final Geothermal geothermal;
    private final Logger logger = LoggerFactory.getLogger(getClass());
    
    public EventHandler(final Geothermal geothermal) {
        this.geothermal = geothermal;
    }
    
    public void handle(final JSONObject event) {
        final JSONObject d = event.getJSONObject("d");
        logger.info("Got audio event: {}", event.getString("t"));
        switch(event.getString("t")) {
            case "AUDIO_CONNECT": {
                // VSU is provided as
                // {
                // "session_id": "238794632894",
                // "vsu": {"full vsu": "event data"}
                // }
                final String sessionId = d.getString("session_id");
                final JSONObject vsu = d.getJSONObject("vsu");
                // TODO: Work out real shard id mappings
                final Core core = geothermal.getCoreManager().getCore(System.getenv("CLIENT_ID"), 0);
                //GeothermalVSU.acceptVSU(core, sessionId, vsu);
                
                ManagedGuild.get(geothermal, vsu.getString("guild_id")).openConnection(core, sessionId, vsu);
                
                break;
            }
            case "AUDIO_DISCONNECT": {
                final String guild = d.getString("guild_id");
                // TODO: Work out real shard id mappings
                final Core core = geothermal.getCoreManager().getCore(System.getenv("CLIENT_ID"), 0);
                core.getAudioManager(guild).closeAudioConnection();
                ManagedGuild.get(geothermal, guild).getHandle().getAudioPlayer().stopTrack();
                geothermal.getNats().pushShardEvent("AUDIO_DISCONNECT", new JSONObject().put("guild_id", guild));
                break;
            }
            case "AUDIO_QUEUE": {
                final ApiContext ctx = ApiContext.fromContext(d.getJSONObject("ctx"));
                final String guild = ctx.getGuild();
                // TODO: Work out real shard id mappings
                final Core core = geothermal.getCoreManager().getCore(System.getenv("CLIENT_ID"), 0);
                ManagedGuild.get(geothermal, guild).playTrack(core, ctx, d.getString("track"), PlayMode.QUEUE);
                break;
            }
            case "AUDIO_PLAY": {
                final ApiContext ctx = ApiContext.fromContext(d.getJSONObject("ctx"));
                final String guild = ctx.getGuild();
                ManagedGuild.get(geothermal, guild).startNextTrack(ctx);
                break;
            }
            case "AUDIO_NOW_PLAYING": {
                final ApiContext ctx = ApiContext.fromContext(d.getJSONObject("ctx"));
                final String guild = ctx.getGuild();
                final QueuedTrack currentTrack = ManagedGuild.get(geothermal, guild).getPlaylist().getCurrentTrack();
                geothermal.getNats().queueTrackEvent(new TrackEvent(Type.AUDIO_TRACK_NOW_PLAYING, ctx, currentTrack.getTrackInfo()));
                break;
            }
        }
    }
}
