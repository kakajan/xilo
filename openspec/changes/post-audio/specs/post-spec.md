# Delta: Post — Audio URL

## REQ-POST-AUDIO-001: Optional Post Audio

**Given** an author creating or updating a post  
**When** they submit with optional `audio_url`  
**Then** the post stores that URL  
**And** GET post responses include `audio_url` when set  
**And** update MAY clear audio by sending empty/`null` `audio_url`.
