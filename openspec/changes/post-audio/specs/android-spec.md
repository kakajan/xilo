# Delta: Android — Post Audio

## REQ-AND-AUDIO-001: Compose Audio Attach

**Given** create/edit post  
**When** the author picks an audio file  
**Then** it uploads via media API and is sent as `audio_url` on create/update  
**And** they can remove it before submit.

## REQ-AND-AUDIO-002: Sticky Player

**Given** a post with `audio_url`  
**When** the user opens post detail  
**Then** a sticky slim player (platform MediaPlayer) provides play/pause, seek, times, and rate  
**And** the player is released when leaving the screen.
