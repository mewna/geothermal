# Geothermal

A better Hotspring. Handles many things like track resolution, playlist management, ... on the server. 

## Events

#### Why the inconsistency in naming things in the JSON?

idk, reasons. 

### Send

Event type may be any of `AUDIO_TRACK_START` `AUDIO_TRACK_STOP` `AUDIO_TRACK_PAUSE` `AUDIO_TRACK_QUEUE` `AUDIO_TRACK_INVALID` `AUDIO_QUEUE_END` `AUDIO_TRACK_NOW_PLAYING`

```JSON
{
    "t": "EVENT_TYPE",
    "d": {
        "ctx": {
            "guild": "1234567890",
            "channel": "1234567890",
            "userId": "1234567890"
        },
        "info": {
            "this": "is just an AudioTrackInfo object from LavaPlayer."
        }
    }
}
```

### Recv

Connect to voice
```JSON
{
    "t": "AUDIO_CONNECT",
    "d": {
        "session_id": "alsidukfhjnalif",
        "vsu": {
            "token": "asdloiufjhasdlikf",
            "guild_id": "1234567890",
            "endpoint": "audio.memes.discord.gg"
        }
    }
}
```

Disconnect from voice
```JSON
{
    "t": "AUDIO_DISCONNECT",
    "d": {
        "guild_id": "1234567890"
    }
}
```

Queue a track to be played later
```JSON
{
    "t": "AUDIO_QUEUE",
    "d": {
        "ctx": {
            "guild_id": "1234567890",
            "channel_id": "1234456790",
            "user_id": "1234567890"
        },
        "track": "url or keywords"
    }
}
```

Play a track immediately
```JSON
{
    "t": "AUDIO_PLAY",
    "d": {
        "ctx": {
            "guild_id": "1234567890",
            "channel_id": "1234456790",
            "user_id": "1234567890"
        },
        "track": "url or keywords"
    }
}
```

## `ctx`???

The server stores the "context" that the track came from - guild, channel, user - so that later when tracks are played, your client doesn't have to have this information stored anywhere. 
