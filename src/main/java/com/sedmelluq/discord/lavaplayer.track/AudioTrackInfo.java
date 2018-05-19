package com.sedmelluq.discord.lavaplayer.track;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public class AudioTrackInfo {
    public final String title;
    public final String author;
    public final long length;
    public final String identifier;
    public final boolean isStream;
    public final String uri;
    
    @JsonCreator
    public AudioTrackInfo(@JsonProperty("title") final String title, @JsonProperty("author") final String author,
                          @JsonProperty("length") final long length, @JsonProperty("identifier") final String identifier,
                          @JsonProperty("isStream") final boolean isStream, @JsonProperty("uri") final String uri) {
        this.title = title;
        this.author = author;
        this.length = length;
        this.identifier = identifier;
        this.isStream = isStream;
        this.uri = uri;
    }
}
