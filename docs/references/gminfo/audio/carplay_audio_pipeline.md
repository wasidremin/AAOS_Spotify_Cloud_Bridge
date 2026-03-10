# GM AAOS CarPlay Audio Pipeline Technical Analysis

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Analysis Date:** January 2026
**Source:** `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`

---

## Executive Summary

GM AAOS implements CarPlay audio through a multi-layer architecture:
1. **CINEMO/NME** - AirPlay protocol and audio decoding (AAC, Opus, PCM)
2. **Android AudioFlinger** - Audio mixing and routing
3. **Harman Audio HAL** - Custom audio processing and output
4. **PulseAudio** - Advanced mixing via AVB (Audio Video Bridging)
5. **External DSP/Amplifier** - Final mixing, ducking, and speaker output

**Key Feature:** Bidirectional audio - iPhone→Vehicle (playback) and Vehicle→iPhone (microphone for Siri/calls)

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                        iPhone (AirPlay Source)                               │
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐    │
│  │ Media Audio  │  │ Navigation   │  │ Siri/Voice   │  │ Phone Call   │    │
│  │ (Music/Pod)  │  │ Prompts      │  │ Assistant    │  │ Audio        │    │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘    │
│         │ AAC-LC          │ AAC-LC          │ Opus           │ AAC-ELD     │
│         │ 44.1/48kHz      │ 48kHz           │ 16-48kHz       │ 16-48kHz    │
└─────────┼─────────────────┼─────────────────┼─────────────────┼────────────┘
          │                 │                 │                 │
          └─────────────────┴─────────────────┴─────────────────┘
                                    │
                                    ▼ AirPlay RTP
┌─────────────────────────────────────────────────────────────────────────────┐
│                    libNmeCarPlay.so (1.0 MB)                                 │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  AirPlayReceiverSession                                             │    │
│  │  - RTP Jitter Buffer (AirPlayJitterBuffer)                          │    │
│  │  - Audio frame reception (OnAudioFrame)                             │    │
│  │  - Stream type routing (MainAudio, AltAudio)                        │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Audio Decoders:                                                             │
│  - NmeCarPlayAudioDecoderConfigAAC (Media)                                  │
│  - NmeCarPlayAudioDecoderConfigAACELD (Telephony)                           │
│  - NmeCarPlayAudioDecoderConfigOpus (Voice Assistant)                       │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ PCM 16/24-bit
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                   NME Audio Libraries                                        │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  libNmeAudioDevice.so (AAudio/OpenSL ES interface)                  │    │
│  │  libNmeAudioRenderer.so (PCM drift correction, buffering)           │    │
│  │  libNmeAudio.so (Sample management, mixing)                         │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Resampling: NmeResample::Create() - Rate conversion to 48kHz               │
│  Volume: OnAudioRampVolume() - Fade in/out, ducking                         │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ PCM 48kHz 16-bit Stereo
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Android AudioFlinger                                      │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Audio Policy Engine                                                │    │
│  │  - Route by USAGE_* attribute to output bus                         │    │
│  │  - Apply preprocessing (AEC/NS on voice)                            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
│                                                                              │
│  Output Buses (AUDIO_DEVICE_OUT_BUS):                                       │
│  ┌────────────┐ ┌────────────┐ ┌────────────┐ ┌────────────┐               │
│  │bus0_media  │ │bus1_nav    │ │bus2_voice  │ │bus4_call   │  ...         │
│  │(Music)     │ │(Navigation)│ │(Siri)      │ │(Phone)     │               │
│  └─────┬──────┘ └─────┬──────┘ └─────┬──────┘ └─────┬──────┘               │
└────────┼──────────────┼──────────────┼──────────────┼───────────────────────┘
         │              │              │              │
         ▼              ▼              ▼              ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              Harman Audio HAL (vendor.hardware.audio@5.0)                    │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  audio.primary.broxton.so                                           │    │
│  │  libharmanpreprocessing_gm.so (AEC, NS, AGC)                        │    │
│  │  libaudiocontrol_gm.so (GM-specific controls)                       │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                      PulseAudio (AVB Integration)                            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  module-combine-sink (Crossbar mixing, 8+ streams)                  │    │
│  │  alsa_output.avb.csm_amp (AVB output to amplifier)                  │    │
│  │  PTP synchronization for real-time audio                            │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ AVB Stream
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              External DSP / Amplifier (CSM - Cabin Sound Manager)            │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  Per-bus mixing, ducking, EQ, fading, speaker routing               │    │
│  │  Priority management (eCall > Phone > Nav > Media)                  │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
                    ┌─────────────────┐
                    │    Speakers     │
                    └─────────────────┘
```

---

## Audio Format Support

### CarPlay Audio Codecs (libNmeCarPlay.so)

#### AAC Formats (Media/Telephony)

| Format ID | Sample Rate | Channels | Use Case |
|-----------|-------------|----------|----------|
| AAC-LC/44100/2 | 44.1 kHz | Stereo | Media playback |
| AAC-LC/48000/2 | 48 kHz | Stereo | Media playback |
| AAC-ELD/16000/1 | 16 kHz | Mono | Telephony (WB) |
| AAC-ELD/24000/1 | 24 kHz | Mono | Telephony (SWB) |
| AAC-ELD/44100/1 | 44.1 kHz | Mono | High-quality voice |
| AAC-ELD/44100/2 | 44.1 kHz | Stereo | FaceTime audio |
| AAC-ELD/48000/1 | 48 kHz | Mono | High-quality voice |
| AAC-ELD/48000/2 | 48 kHz | Stereo | FaceTime audio |

#### Opus Formats (Voice Assistant)

| Format ID | Sample Rate | Channels | Use Case |
|-----------|-------------|----------|----------|
| OPUS/16000/1 | 16 kHz | Mono | Siri voice commands |
| OPUS/24000/1 | 24 kHz | Mono | Enhanced voice |
| OPUS/48000/1 | 48 kHz | Mono | High-quality voice |

#### PCM Formats (Raw/Fallback)

| Format ID | Sample Rate | Bits | Channels | Use Case |
|-----------|-------------|------|----------|----------|
| PCM/8000/16/1 | 8 kHz | 16 | Mono | Legacy telephony (NB) |
| PCM/16000/16/1 | 16 kHz | 16 | Mono | Wideband voice |
| PCM/24000/16/2 | 24 kHz | 16 | Stereo | Super wideband |
| PCM/44100/16/2 | 44.1 kHz | 16 | Stereo | CD quality |
| PCM/48000/16/2 | 48 kHz | 16 | Stereo | Standard output |
| PCM/48000/24/2 | 48 kHz | 24 | Stereo | High resolution |

---

## Audio Bus Routing (CarPlay)

### Output Routing

| CarPlay Audio Type | Android Usage | Destination Bus | Priority |
|--------------------|---------------|-----------------|----------|
| Media (Music/Podcasts) | USAGE_MEDIA | bus0_media_out | Normal |
| Navigation Prompts | USAGE_ASSISTANCE_NAVIGATION_GUIDANCE | bus1_navigation_out | High |
| Siri/Voice Assistant | USAGE_ASSISTANT | bus2_voice_command_out | High |
| Ringtone | USAGE_NOTIFICATION_TELEPHONY_RINGTONE | bus3_call_ring_out | Urgent |
| Phone Call Audio | USAGE_VOICE_COMMUNICATION | bus4_call_out | Critical |
| Alerts | USAGE_NOTIFICATION | bus6_notification_out | High |

### Bus Configuration

All buses operate at **48 kHz, PCM 16-bit, Stereo**:

```xml
<mixPort name="mixport_bus0_media_out" role="source"
         flags="AUDIO_OUTPUT_FLAG_PRIMARY">
    <profile format="AUDIO_FORMAT_PCM_16_BIT"
             samplingRates="48000"
             channelMasks="AUDIO_CHANNEL_OUT_STEREO"/>
</mixPort>
```

---

## Microphone Uplink (Vehicle → iPhone)

### Input Device Configuration

| Device | Type | Address | Sample Rates | Use Case |
|--------|------|---------|--------------|----------|
| Built-In Mic | BUILTIN_MIC | bottom | 16k, 24k, 48k Hz | General input |
| Echo-Reference | ECHO_REFERENCE | - | 48 kHz | AEC reference |
| mic_for_vc | BUS | Voice_Uplink | 8k, 16k, 24k Hz | CarPlay phone calls |
| mic_for_raw | DEFAULT | - | 48 kHz | Raw capture |

### Voice Call Uplink Path

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    Vehicle Microphone Array                                  │
│                    (Harman/Bosch beamforming)                               │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ 48kHz PCM
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│              Harman Preprocessing (libharmanpreprocessing_gm.so)             │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  AEC (Acoustic Echo Cancellation)                                   │    │
│  │  - Reference: Echo-Reference Mic (speaker output)                   │    │
│  │  - Removes car speaker audio from microphone                        │    │
│  │  NS (Noise Suppression)                                             │    │
│  │  - Removes road noise, engine noise, HVAC                           │    │
│  │  AGC (Automatic Gain Control)                                       │    │
│  │  - Normalizes voice levels                                          │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ Processed PCM
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    mic_for_vc (Voice Communication Bus)                      │
│                    8kHz/16kHz/24kHz Mono                                     │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │
                                ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                    libNmeCarPlay.so                                          │
│  ┌─────────────────────────────────────────────────────────────────────┐    │
│  │  NmeCarPlayAudioEncoderConfigAACELD - Telephony encoding            │    │
│  │  NmeCarPlayAudioEncoderConfigOpus - Siri voice encoding             │    │
│  │  NmeEncodeOpus - Opus encoder implementation                        │    │
│  │  _AirPlayReceiver_SendAudio() - RTP transmission                    │    │
│  └─────────────────────────────────────────────────────────────────────┘    │
└───────────────────────────────┬─────────────────────────────────────────────┘
                                │ AirPlay RTP (Encoded audio)
                                ▼
                         ┌─────────────────┐
                         │    iPhone       │
                         │  (Siri/Phone)   │
                         └─────────────────┘
```

### CarPlay Audio Session Modes

From libNmeCarPlay.so:
```cpp
OnSessionSetInputMode()  // Configure input mode for session
SetHIDInputMode()        // HID input mode (push-to-talk)
InitAudioCapture()       // Initialize capture for session
```

---

## Jitter Buffer Management

### RTP Jitter Buffer (libNmeCarPlay.so)

```cpp
// Jitter buffer logging format
"%s RTPJitterBuffer %3d ms (+) ts %u seq %u"  // Buffer add
"%s RTPJitterBuffer %3d ms (-)"                // Buffer remove

// Excess handling
"Jitter Buffer Discard Excess, prevSize=%d(%dms) newSize=%d(%dms)"
```

**Configuration:**
- Buffer logging rate: `AirPlayJitterBuffer:rate=3;1000`
- Latency parameters: `inputLatencyMicros`, `outputLatencyMicros`, `audioLatencyMs`

### Latency Management

```cpp
// Latency offset logging
"Audio Latency Offset %u, Sender %d, Relative %d"

// Device period validation
"Device period %dms is bigger than required latency %dms"
```

---

## Audio Decoder Pipeline

### NME Audio Libraries

| Library | Size | Function |
|---------|------|----------|
| `libNmeAudio.so` | 325 KB | Core sample handling, mixing |
| `libNmeAudioAAC.so` | 322 KB | AAC-LC/AAC-ELD decoding |
| `libNmeAudioDevice.so` | 464 KB | AAudio/OpenSL ES output |
| `libNmeAudioRenderer.so` | 159 KB | PCM rendering, drift correction |
| `libNmeAudioMisc.so` | 46 KB | PCM codec utilities |

### Decoder Flow

```cpp
// AAC decoder configuration (libNmeAudioAAC.so)
NmeCreateCodecAAC()
codec->Initialize(mediaType)
codec->SetPrimaryConfig(AudioSpecificConfig)

// Frame processing
frame->Write(data, size)
frame->ConsumePTS()
frame->NormalizeBuffer()

// PCM delivery
DeliverPCM()
```

### Resampling

From `libNmeAudioDevice.so`:
```cpp
NmeResample::Create()  // Create resampler instance
// Supports conversion to/from any sample rate to 48kHz
```

---

## Audio Processing Effects

### Harman Preprocessing (Voice Input)

| Effect | UUID | Purpose |
|--------|------|---------|
| AEC | `0f8d0d2a-59e5-45fe-b6e4-248c8a799109` | Echo cancellation |
| NS | `1d97bb0b-9e2f-4403-9ae3-58c2554306f8` | Noise suppression |
| AGC | `0dd49521-8c59-40b1-b403-e08d5f01875e` | Gain control |

**Configuration:**
```xml
<preprocess>
    <stream type="voice_communication">
        <apply effect="aec"/>
        <apply effect="ns"/>
    </stream>
    <stream type="voice_recognition">
        <apply effect="aec"/>
        <apply effect="ns"/>
    </stream>
</preprocess>
```

### NXP Effects Bundle (Output)

| Effect | Purpose |
|--------|---------|
| Bass Boost | Low frequency enhancement |
| Virtualizer | Spatial audio |
| Equalizer | Frequency response |
| Volume | Level control |
| Reverb | Ambience |

---

## CarPlay SCD Configuration Files

**Location:** `/vendor/etc/scd/`

### Telephony Configurations

| File | Description |
|------|-------------|
| `SSE_HF_GM_INFO3_CarPlayTelWB.scd` | CarPlay Wideband Telephony |
| `SSE_HF_GM_INFO3_CarPlayTelNB.scd` | CarPlay Narrowband Telephony |
| `SSE_HF_GM_INFO3_CarPlayTelWB_48khz_DL.scd` | WB 48kHz Downlink |
| `SSE_HF_GM_INFO3_CarPlayTelNB_48khz_DL.scd` | NB 48kHz Downlink |

### WiFi CarPlay Configurations

| File | Description |
|------|-------------|
| `SSE_HF_GM_INFO3_WiFi_CarPlayTelWB.scd` | WiFi CarPlay WB |
| `SSE_HF_GM_INFO3_WiFi_CarPlayTelSWB.scd` | WiFi CarPlay Super-WB |
| `SSE_HF_GM_INFO3_WiFi_CarPlayFT_SWB_48khz_DL.scd` | WiFi FaceTime SWB |

### FaceTime Audio

| File | Description |
|------|-------------|
| `SSE_HF_GM_INFO3_CarPlayFT_SWB.scd` | FaceTime Super-Wideband |
| `SSE_HF_GM_INFO3_CarPlayFT_SWB_48khz_DL.scd` | FaceTime 48kHz Downlink |

---

## PulseAudio / AVB Integration

### Service Configuration

```rc
# /vendor/etc/init/pulseaudio.rc
service pulseaudio /vendor/bin/pulseaudio
    class hal
    user audioserver
    group audio system
    ioprio rt 7
```

### AVB Stream Handler

```
Process: avb_streamhandler_app (PID 337)
User: audioserver
Memory: 11.5 MB

Output Sink: alsa_output.avb.csm_amp
- PTP (Precision Time Protocol) synchronized
- Real-time audio streaming to amplifier
```

### Crossbar Mixing

```
pulseaudio: [combine] module-combine-sink.c:
    debug: current 9 streams into crossbar 8,dumping state=0.
    stream[8:pcmChime_p] Channel start 6 Channels 2.
    channel [6] RMS [0.00] samples [384000] in past 8 seconds
```

---

## Error Handling

### Audio Frame Errors (libNmeCarPlay.so)

```cpp
// Decoder errors
"configure() of decoder failed"
"OnAudioFrame() could not create resampler"
"OnAudioFrame() could not write to resampler"

// Format errors
"nme_carplay_audio_prepare() wrong bit count"
"nme_carplay_audio_prepare() wrong format flags"
"nme_carplay_audio_prepare() wrong packet count"

// Capture errors
"Start(): Failed to init capture: %e"
"InitAudioCapture Create device failed: %e"

// Thread errors
"### Alt audio start failed: %#m"
"### Main audio start failed: %#m"
"### General audio setup failed: %#m"
```

### PCM Delivery Errors (libNmeAudio.so)

```cpp
// Sample dropping
"DeliverPCM() - drop %zu samples (before range)"
"DeliverPCM() - drop %zu samples (after range)"

// Timing issues
"ReceivePCM() -> large gap %T at %T"
"ReceivePCM() -> large overlap %T at %T"
"ReceivePCM() -> small gap %T at %T"

// S/PDIF
"ReceivePCM() - drop S/PDIF frame"
```

### Drift Correction (libNmeAudioRenderer.so)

```cpp
"OnTimerCheckDriftPCM(), time: %T s, drift: %T ms
    (playback %s of stream clock), ival: %.0f ppm,
    qval: %.3f ms, adjust: %d ppm %s"
```

---

## Latency Characteristics

### System Latency

| Component | Latency |
|-----------|---------|
| AirPlay RTP Jitter Buffer | 20-50 ms |
| NME Audio Decoder | 5-10 ms |
| AudioFlinger Mix | 8 ms |
| HAL Output Buffer | 8 ms |
| PulseAudio/AVB | 2-5 ms |
| DSP Processing | 5-10 ms |
| **Total (estimated)** | **~50-90 ms** |

### Configuration Options

```cpp
// libNmeAudio.so
audio_delay_ms                    // Base delay
audio_jitter_threshold_ms        // Jitter tolerance
audio_error_fadein_ms            // Error recovery fade-in
audio_error_fadeout_ms           // Error recovery fade-out

// libNmeAudioDevice.so
audio_device_buffer_ms           // Device buffer size
audio_device_period_ms           // Device period
audio_capture_device_buffer_ms   // Capture buffer
audio_capture_device_period_ms   // Capture period

// libNmeAudioRenderer.so
audio_renderer_buffer_ms         // Renderer buffer
audio_http_prefetch_buffer_ms    // HTTP prebuffer
buffer_livestream_prebuffer_ms   // Live stream buffer
buffer_low_latency_prebuffer_ms  // Low-latency buffer
```

---

## Ducking Behavior

### Priority-Based Ducking

| Active Audio | Ducked Audio | Duck Level |
|--------------|--------------|------------|
| Emergency Call (bus8) | All others | Mute |
| Phone Call (bus4) | Media (bus0) | -24 dB |
| Navigation (bus1) | Media (bus0) | -12 dB |
| Voice Assistant (bus2) | Media (bus0) | -12 dB |
| Notification (bus6) | Media (bus0) | -6 dB |

### Volume Ramping

```cpp
// libNmeCarPlay.so
"Ducking audio to %f within %f seconds"

OnAudioRampVolume()  // Volume fade callback
```

---

## Debug Capabilities

### Audio Dumping

```cpp
// Dump file format (libNmeCarPlay.so)
"AsyncAAudioSink_%lld_%uhz_%uchannels_%ubits.dump"
// Example: AsyncAAudioSink_12345_48000hz_2channels_16bits.dump

// Error on dump
"Failed to open %s (%e) for audio dumping"
```

### Triggering Audio Dump

```bash
# Set property to trigger dump
setprop vendor.audiodump.status start
# Runs: /vendor/bin/Onekey_audio_dump.sh
```

---

## Related Documentation

- [audio_subsystem.md](audio_subsystem.md) - Core audio system details
- [audio_codecs.md](audio_codecs.md) - Codec specifications
- [audio_effects.md](audio_effects.md) - Audio effects processing
- [automotive_audio.md](automotive_audio.md) - AAOS audio architecture
- [../video/carplay_video_pipeline.md](../video/carplay_video_pipeline.md) - Video pipeline
- [../video/cinemo_nme_framework.md](../video/cinemo_nme_framework.md) - CINEMO framework

---

## Data Sources

**Extracted Partitions:**
- `/vendor/etc/audio_policy_configuration.xml`
- `/vendor/etc/audio_effects.xml`
- `/vendor/etc/scd/*.scd`
- `/system/lib64/libNmeAudio*.so`
- `/system/lib64/libNmeCarPlay.so`

**ADB Dumps:**
- Process list (audioserver, pulseaudio, vehicleaudiocontrol)
- Service list (audio, media.audio_flinger)
- Logcat (PulseAudio, CarPlay_PlayerManager)

**Binary Analysis:**
- `strings` on NME audio libraries
- Audio format string extraction

**Source:** `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`
