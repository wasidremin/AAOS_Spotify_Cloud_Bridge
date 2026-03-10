# GM Infotainment Audio Codec Specifications

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Audio Codec Overview

All audio codecs on this device are software-based (CPU). There are no hardware audio decoders or encoders. The codecs are implemented using the Android Codec2 (C2) framework with legacy OMX aliases for compatibility.

---

## Audio Decoders

### AAC Decoder

| Property | `c2.android.aac.decoder` |
|----------|--------------------------|
| MIME Type | audio/mp4a-latm |
| Aliases | OMX.google.aac.decoder |
| Owner | codec2::software |
| Rank | 8 (software) |
| Max Channels | 8 |
| Sample Rates | 7350, 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 Hz |
| Bitrate Range | 8 - 960 Kbps |

**Supported AAC Profiles:**

| Profile ID | Profile Name | Description |
|------------|--------------|-------------|
| 2 | AAC-LC | Low Complexity (most common) |
| 5 | HE-AAC | High Efficiency AAC (SBR) |
| 29 | HE-AAC v2 | HE-AAC with Parametric Stereo |
| 23 | AAC-LD | Low Delay (for communication) |
| 39 | AAC-ELD | Enhanced Low Delay |
| 20 | ER AAC Scalable | Error Resilient Scalable |
| 42 | xHE-AAC | Extended High Efficiency |

---

### MP3 Decoder

| Property | `c2.android.mp3.decoder` |
|----------|--------------------------|
| MIME Type | audio/mpeg |
| Aliases | OMX.google.mp3.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 2 (Stereo) |
| Sample Rates | 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 Hz |
| Bitrate Range | 8 - 320 Kbps |

---

### Opus Decoder

| Property | `c2.android.opus.decoder` |
|----------|---------------------------|
| MIME Type | audio/opus |
| Aliases | OMX.google.opus.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 8 |
| Sample Rates | 8000, 12000, 16000, 24000, 48000 Hz |
| Bitrate Range | 6 - 510 Kbps |

**Note:** Opus is excellent for voice and music at low bitrates.

---

### Vorbis Decoder

| Property | `c2.android.vorbis.decoder` |
|----------|------------------------------|
| MIME Type | audio/vorbis |
| Aliases | OMX.google.vorbis.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 8 |
| Sample Rates | 8000 - 96000 Hz |
| Bitrate Range | 32 - 500 Kbps |

---

### FLAC Decoder

| Property | `c2.android.flac.decoder` |
|----------|---------------------------|
| MIME Type | audio/flac |
| Aliases | OMX.google.flac.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 8 |
| Sample Rates | 1 - 655,350 Hz |
| Bitrate Range | 1 - 21,000 Kbps |

**Note:** FLAC is lossless compression.

---

### AMR-NB Decoder

| Property | `c2.android.amrnb.decoder` |
|----------|----------------------------|
| MIME Type | audio/3gpp |
| Aliases | OMX.google.amrnb.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 1 (Mono) |
| Sample Rate | 8000 Hz |
| Bitrate Range | 4.75 - 12.2 Kbps |

**Use Case:** Narrowband voice (telephony)

---

### AMR-WB Decoder

| Property | `c2.android.amrwb.decoder` |
|----------|----------------------------|
| MIME Type | audio/amr-wb |
| Aliases | OMX.google.amrwb.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 1 (Mono) |
| Sample Rate | 16000 Hz |
| Bitrate Range | 6.6 - 23.85 Kbps |

**Use Case:** Wideband voice (HD Voice)

---

### G.711 A-law Decoder

| Property | `c2.android.g711.alaw.decoder` |
|----------|--------------------------------|
| MIME Type | audio/g711-alaw |
| Aliases | OMX.google.g711.alaw.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 6 |
| Sample Rates | 8000 - 48000 Hz |
| Bitrate | 64 Kbps (per channel) |

**Use Case:** European telephony standard

---

### G.711 μ-law Decoder

| Property | `c2.android.g711.mlaw.decoder` |
|----------|--------------------------------|
| MIME Type | audio/g711-mlaw |
| Aliases | OMX.google.g711.mlaw.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 6 |
| Sample Rates | 8000 - 48000 Hz |
| Bitrate | 64 Kbps (per channel) |

**Use Case:** North American telephony standard

---

### Raw PCM Decoder

| Property | `c2.android.raw.decoder` |
|----------|--------------------------|
| MIME Type | audio/raw |
| Aliases | OMX.google.raw.decoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 8 |
| Sample Rates | 8000 - 96000 Hz |
| Bitrate Range | 1 - 10,000 Kbps |

**Note:** Pass-through for uncompressed audio.

---

## Audio Encoders

### AAC Encoder

| Property | `c2.android.aac.encoder` |
|----------|--------------------------|
| MIME Type | audio/mp4a-latm |
| Aliases | OMX.google.aac.encoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 6 (5.1 surround) |
| Sample Rates | 8000, 11025, 12000, 16000, 22050, 24000, 32000, 44100, 48000 Hz |
| Bitrate Range | 8 - 512 Kbps |

**Supported Profiles for Encoding:**
- AAC-LC (Low Complexity)
- HE-AAC
- AAC-ELD

---

### AMR-NB Encoder

| Property | `c2.android.amrnb.encoder` |
|----------|----------------------------|
| MIME Type | audio/3gpp |
| Aliases | OMX.google.amrnb.encoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 1 (Mono) |
| Sample Rate | 8000 Hz |
| Bitrate Range | 4.75 - 12.2 Kbps |
| Bitrate Mode | CBR (Constant) |

**AMR-NB Modes:**

| Mode | Bitrate |
|------|---------|
| 0 | 4.75 Kbps |
| 1 | 5.15 Kbps |
| 2 | 5.90 Kbps |
| 3 | 6.70 Kbps |
| 4 | 7.40 Kbps |
| 5 | 7.95 Kbps |
| 6 | 10.2 Kbps |
| 7 | 12.2 Kbps |

---

### AMR-WB Encoder

| Property | `c2.android.amrwb.encoder` |
|----------|----------------------------|
| MIME Type | audio/amr-wb |
| Aliases | OMX.google.amrwb.encoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 1 (Mono) |
| Sample Rate | 16000 Hz |
| Bitrate Range | 6.6 - 23.85 Kbps |
| Bitrate Mode | CBR (Constant) |

**AMR-WB Modes:**

| Mode | Bitrate |
|------|---------|
| 0 | 6.60 Kbps |
| 1 | 8.85 Kbps |
| 2 | 12.65 Kbps |
| 3 | 14.25 Kbps |
| 4 | 15.85 Kbps |
| 5 | 18.25 Kbps |
| 6 | 19.85 Kbps |
| 7 | 23.05 Kbps |
| 8 | 23.85 Kbps |

---

### FLAC Encoder

| Property | `c2.android.flac.encoder` |
|----------|---------------------------|
| MIME Type | audio/flac |
| Aliases | OMX.google.flac.encoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 2 (Stereo) |
| Sample Rates | 1 - 655,350 Hz |
| Bitrate | Lossless (1-21 Mbps) |
| Bitrate Mode | CQ (Constant Quality) |
| Complexity | 0-8 (default: 5) |

---

### Opus Encoder

| Property | `c2.android.opus.encoder` |
|----------|---------------------------|
| MIME Type | audio/opus |
| Aliases | OMX.google.opus.encoder |
| Owner | codec2::software |
| Rank | 8 |
| Max Channels | 2 (Stereo) |
| Sample Rates | 8000, 12000, 16000, 24000, 48000 Hz |
| Bitrate Range | 6 - 510 Kbps |
| Bitrate Modes | CBR, VBR |

---

## Codec Comparison

### Decoder Feature Matrix

| Codec | Channels | Max Rate | Latency | Quality | Use Case |
|-------|----------|----------|---------|---------|----------|
| AAC | 8 | 48 kHz | Low | Excellent | Music, streaming |
| MP3 | 2 | 48 kHz | Low | Good | Legacy music |
| Opus | 8 | 48 kHz | Very Low | Excellent | VoIP, streaming |
| Vorbis | 8 | 96 kHz | Low | Excellent | Gaming, web |
| FLAC | 8 | 655 kHz | Low | Lossless | Archival |
| AMR-NB | 1 | 8 kHz | Very Low | Fair | Telephony |
| AMR-WB | 1 | 16 kHz | Very Low | Good | HD Voice |
| G.711 | 6 | 48 kHz | Very Low | Fair | Telephony |

### Encoder Feature Matrix

| Codec | Channels | Best For | Bitrate | Quality |
|-------|----------|----------|---------|---------|
| AAC | 6 | Music, general | 128-256 Kbps | Excellent |
| Opus | 2 | VoIP, low latency | 24-128 Kbps | Excellent |
| AMR-NB | 1 | Voice calls | 12.2 Kbps | Fair |
| AMR-WB | 1 | HD Voice | 23.85 Kbps | Good |
| FLAC | 2 | Archival | Lossless | Perfect |

---

## Recommendations

### CarPlay/Android Auto Audio

| Use Case | Recommended Codec | Configuration |
|----------|-------------------|---------------|
| Music Playback | AAC-LC | 48 kHz, Stereo, 256 Kbps |
| Voice Assistant | Opus | 16 kHz, Mono, 24 Kbps |
| Phone Calls | AMR-WB | 16 kHz, Mono, 23.85 Kbps |
| Navigation | AAC-LC | 48 kHz, Stereo, 128 Kbps |

### Microphone Recording

| Use Case | Recommended Codec | Configuration |
|----------|-------------------|---------------|
| Voice Recognition | Opus | 16 kHz, Mono, 24 Kbps |
| Voice Call Uplink | AMR-WB | 16 kHz, Mono |
| General Recording | AAC-LC | 48 kHz, Stereo, 128 Kbps |

### Streaming Formats

| Protocol | Recommended Container | Audio Codec |
|----------|----------------------|-------------|
| HLS | fMP4, TS | AAC-LC, HE-AAC |
| DASH | fMP4 | AAC-LC, Opus |
| WebRTC | - | Opus |
| SIP/VoIP | RTP | AMR-WB, Opus, G.711 |

---

## Legacy OMX Aliases

For backwards compatibility, C2 codecs have OMX aliases:

| C2 Codec | OMX Alias |
|----------|-----------|
| c2.android.aac.decoder | OMX.google.aac.decoder |
| c2.android.mp3.decoder | OMX.google.mp3.decoder |
| c2.android.opus.decoder | OMX.google.opus.decoder |
| c2.android.vorbis.decoder | OMX.google.vorbis.decoder |
| c2.android.flac.decoder | OMX.google.flac.decoder |
| c2.android.amrnb.decoder | OMX.google.amrnb.decoder |
| c2.android.amrwb.decoder | OMX.google.amrwb.decoder |
| c2.android.g711.alaw.decoder | OMX.google.g711.alaw.decoder |
| c2.android.g711.mlaw.decoder | OMX.google.g711.mlaw.decoder |
| c2.android.raw.decoder | OMX.google.raw.decoder |
| c2.android.aac.encoder | OMX.google.aac.encoder |
| c2.android.amrnb.encoder | OMX.google.amrnb.encoder |
| c2.android.amrwb.encoder | OMX.google.amrwb.encoder |
| c2.android.flac.encoder | OMX.google.flac.encoder |
| c2.android.opus.encoder | OMX.google.opus.encoder |
