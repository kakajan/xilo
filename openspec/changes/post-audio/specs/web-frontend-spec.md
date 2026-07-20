# Delta: Web — Post Audio

## REQ-WEB-AUDIO-001: Editor Audio Attach

**Given** the post editor  
**When** the author selects an audio file  
**Then** it uploads via `/api/media/upload` and is bound as `audio_url`  
**And** they can replace or remove it before publish.

## REQ-WEB-AUDIO-002: Sticky Player

**Given** a published post with `audio_url`  
**When** a reader opens the post detail page  
**Then** a narrow sticky player shows play/pause, seek, times, and rate controls  
**And** it stacks above the sticky reaction bar without covering mobile bottom nav.
