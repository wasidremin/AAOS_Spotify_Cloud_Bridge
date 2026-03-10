# GM Infotainment Audio Subsystem Specifications

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Audio System Overview

The GM infotainment system uses Android Automotive's multi-zone audio architecture with dedicated audio buses for different audio contexts. Audio is routed through an external DSP/amplifier system via AUDIO_DEVICE_OUT_BUS interfaces.

---

## Audio HAL Configuration

### HAL Version
- **Primary HAL:** Version 3.0
- **Configuration:** `/vendor/etc/audio_policy_configuration.xml`

### System Properties

```properties
audio.safemedia.bypass=true
init.svc.audioserver=running
init.svc.vehicleaudiocontrol=running
```

---

## AudioFlinger Configuration

### Primary Output Thread (AudioOut_D)

| Property | Value |
|----------|-------|
| I/O Handle | 13 |
| Sample Rate | 48000 Hz |
| Format | PCM_16_BIT (0x1) |
| Channel Count | 2 (Stereo) |
| Channel Mask | front-left, front-right |
| HAL Frame Count | 384 frames |
| HAL Buffer Size | 1536 bytes |
| Processing Format | PCM_FLOAT |
| Output Device | AUDIO_DEVICE_OUT_BUS |

### Timing Characteristics

| Metric | Value |
|--------|-------|
| Mix Period | 8.00 ms |
| Latency | 24.00 ms |
| Normal Frame Count | 768 frames |
| Fast Track Count | 8 maximum |
| Standby Delay | 0 ns |

### FastMixer Thread

| Property | Value |
|----------|-------|
| Sample Rate | 48000 Hz |
| Frame Count | 384 |
| Warmup Time | ~40.5 ms |
| Warmup Cycles | 6 |

---

## Output Audio Buses

The system uses 12 dedicated output buses for audio routing:

| Bus Address | Purpose | Usage |
|-------------|---------|-------|
| `bus0_media_out` | Media playback | MEDIA, GAME, UNKNOWN |
| `bus1_navigation_out` | Navigation guidance | ASSISTANCE_NAVIGATION_GUIDANCE |
| `bus2_voice_command_out` | Voice assistant | ASSISTANT, ACCESSIBILITY |
| `bus3_call_ring_out` | Call ringtone | NOTIFICATION_TELEPHONY_RINGTONE |
| `bus4_call_out` | Voice calls | VOICE_COMMUNICATION |
| `bus5_alarm_out` | Alarms | ALARM |
| `bus6_notification_out` | Notifications | NOTIFICATION variants |
| `bus7_system_sound_out` | System sounds | ASSISTANCE_SONIFICATION |
| `bus8_ecall_ring_out` | Emergency call ring | eCall |
| `bus11_mix_unduck_out` | Mix unduck | Priority audio |
| `bus12_audio_cue_out` | Audio cues | System cues |
| `bus13_high_priority_mutex_out` | High priority mutex | Critical audio |

### Bus Configuration Details

All output buses share common configuration:

| Property | Value |
|----------|-------|
| Format | AUDIO_FORMAT_PCM_16_BIT |
| Sample Rate | 48000 Hz |
| Channel Mask | AUDIO_CHANNEL_OUT_STEREO |
| Gain Range | 0 to +63 dB |
| Default Gain | +30 dB |
| Step Size | 1 dB (100 mB) |

---

## Input Audio Devices

### Built-In Microphone

| Property | Value |
|----------|-------|
| Device Type | AUDIO_DEVICE_IN_BUILTIN_MIC |
| Address | bottom |
| Format | PCM_16_BIT |
| Sample Rates | 8000, 16000, 24000, 48000 Hz |
| Channel Masks | Mono, Stereo, Front-Back |

### Input Buses

| Bus Address | Purpose | Sample Rate | Channels |
|-------------|---------|-------------|----------|
| `bus3_sxm_in` | SiriusXM | 48000 Hz | Stereo |
| `bus4_lvm_in` | LVM | 48000 Hz | Stereo |
| `bus5_tcp_phone_dnlink_in` | Phone downlink | 48000 Hz | Stereo |
| `bus6_tcp_prompt_in` | TCP prompt | 48000 Hz | Mono |
| `bus7_bt_dnlink_in` | Bluetooth downlink | 48000 Hz | Stereo |
| `bus8_tuner_in` | FM Tuner | 48000 Hz | Stereo |
| `bus9_tcp_phone_emerg_dnlink_in` | Emergency phone | 48000 Hz | Stereo |
| `bus10_tuner_am_in` | AM Tuner | 48000 Hz | Stereo |
| `bus11_lvm_prompt_in` | LVM prompt | 48000 Hz | Mono |
| `bus12_tcp_mixprompt_in` | Mix prompt | 48000 Hz | Mono |
| `bus13_dab_in` | DAB radio | 48000 Hz | Stereo |
| `bus14_rsi_in` | RSI | 48000 Hz | Stereo |

### Special Input Ports

| Port | Type | Purpose | Sample Rates |
|------|------|---------|--------------|
| Echo-Reference Mic | ECHO_REFERENCE | AEC reference | 48000 Hz |
| mic_for_vc | BUS (Voice_Uplink) | Voice call uplink | 8000-24000 Hz |
| mic_for_raw | DEFAULT | Raw microphone | 48000 Hz |

---

## Audio Codecs

### Audio Decoders

| Codec | MIME Type | Max Channels | Sample Rates | Bitrate |
|-------|-----------|--------------|--------------|---------|
| AAC | audio/mp4a-latm | 8 | 7.35-48 kHz | 8-960 Kbps |
| MP3 | audio/mpeg | 2 | 8-48 kHz | 8-320 Kbps |
| Opus | audio/opus | 8 | 48 kHz | 6-510 Kbps |
| Vorbis | audio/vorbis | 8 | 8-96 kHz | 32-500 Kbps |
| FLAC | audio/flac | 8 | 1-655 kHz | 1-21 Mbps |
| AMR-NB | audio/3gpp | 1 | 8 kHz | 4.75-12.2 Kbps |
| AMR-WB | audio/amr-wb | 1 | 16 kHz | 6.6-23.85 Kbps |
| G.711 A-law | audio/g711-alaw | 6 | 8-48 kHz | 64 Kbps |
| G.711 μ-law | audio/g711-mlaw | 6 | 8-48 kHz | 64 Kbps |
| PCM Raw | audio/raw | 8 | 8-192 kHz | - |

### AAC Profiles Supported

| Profile ID | Profile Name |
|------------|--------------|
| 2 | AAC-LC (Low Complexity) |
| 5 | HE-AAC (High Efficiency) |
| 29 | HE-AAC v2 (with Parametric Stereo) |
| 23 | AAC-LD (Low Delay) |
| 39 | AAC-ELD (Enhanced Low Delay) |
| 42 | xHE-AAC (Extended HE) |

### Audio Encoders

| Codec | MIME Type | Max Channels | Sample Rates | Bitrate |
|-------|-----------|--------------|--------------|---------|
| AAC | audio/mp4a-latm | 6 | 8-48 kHz | 8-512 Kbps |
| AMR-NB | audio/3gpp | 1 | 8 kHz | 4.75-12.2 Kbps |
| AMR-WB | audio/amr-wb | 1 | 16 kHz | 6.6-23.85 Kbps |
| FLAC | audio/flac | 2 | 1-655 kHz | Lossless |
| Opus | audio/opus | 2 | 8-48 kHz | 6-510 Kbps |

---

## Audio Streams and Volume

### Stream Types

| Stream | Index Range | Default | Mute Affected |
|--------|-------------|---------|---------------|
| VOICE_CALL | 1-40 | 40 | No |
| SYSTEM | 0-40 | 40 | Yes |
| RING | 0-40 | 40 | Yes |
| MUSIC | 0-40 | 40 | Yes |
| ALARM | 0-40 | 40 | Yes |
| NOTIFICATION | 0-40 | 40 | Yes |
| BLUETOOTH_SCO | 0-40 | 40 | Yes |
| SYSTEM_ENFORCED | 0-40 | 40 | Yes |
| DTMF | 0-40 | 40 | Yes |
| TTS | 0-40 | 40 | No |
| ACCESSIBILITY | 1-40 | 40 | No |
| ASSISTANT | 0-40 | 40 | No |

### Volume Configuration

| Property | Value |
|----------|-------|
| Fixed Volume Mode | Enabled |
| Safe Media Volume | Disabled (bypassed) |
| Ringer Mode | NORMAL |
| Use Fixed Volume | true |

---

## Audio Effects

### Loaded Effect Libraries

| Library | Path | Purpose |
|---------|------|---------|
| pre_processing | libharmanpreprocessing_gm.so | Harman audio preprocessing |
| dynamics_processing | libdynproc.so | Dynamics processing |
| loudness_enhancer | libldnhncr.so | Loudness enhancement |
| downmix | libdownmix.so | Multichannel downmix |
| visualizer | libvisualizer.so | Audio visualization |
| reverb | libreverbwrapper.so | Reverb effects |
| bundle | libbundlewrapper.so | NXP effects bundle |

### Available Effects

| Effect Name | UUID | Type | Vendor |
|-------------|------|------|--------|
| Noise Suppression | 1d97bb0b-9e2f-4403-9ae3-58c2554306f8 | NS | Harman |
| Acoustic Echo Canceler | 0f8d0d2a-59e5-45fe-b6e4-248c8a799109 | AEC | Harman |
| Automatic Gain Control | 0dd49521-8c59-40b1-b403-e08d5f01875e | AGC | Harman |
| Bass Boost | 8631f300-72e2-11df-b57e-0002a5d5c51b | BB | NXP |
| Virtualizer | 1d4033c0-8557-11df-9f2d-0002a5d5c51b | VIRT | NXP |
| Equalizer | ce772f20-847d-11df-bb17-0002a5d5c51b | EQ | NXP |
| Volume | 119341a0-8469-11df-81f9-0002a5d5c51b | VOL | NXP |
| Environmental Reverb (Aux) | 4a387fc0-8ab3-11df-8bad-0002a5d5c51b | REVERB | NXP |
| Environmental Reverb (Insert) | c7a511a0-a3bb-11df-860e-0002a5d5c51b | REVERB | NXP |
| Preset Reverb (Aux) | f29a1400-a3bb-11df-8ddc-0002a5d5c51b | REVERB | NXP |
| Preset Reverb (Insert) | 172cdf00-a3bc-11df-a72f-0002a5d5c51b | REVERB | NXP |
| Visualizer | d069d9e0-8329-11df-9168-0002a5d5c51b | VIS | AOSP |
| Downmix | 93f04452-e4fe-41cc-91f9-e475b6d1d69f | DOWNMIX | AOSP |
| Loudness Enhancer | fa415329-2034-4bea-b5dc-5b381c8d1e2c | LE | AOSP |
| Dynamics Processing | e0e6539b-1781-7261-676f-6d7573696340 | DYN | AOSP |

### Pre-Processing Configuration

Applied to voice streams:

| Stream Type | Effects Applied |
|-------------|-----------------|
| voice_communication | AEC, NS |
| voice_recognition | AEC, NS |

---

## Audio Policy Strategies

### Product Strategies

| Strategy | ID | Audio Usage | Output |
|----------|----|--------------| -------|
| oem_traffic_anouncement | 14 | NAVIGATION_GUIDANCE | bus0_media_out |
| oem_strategy_1 | 15 | NAVIGATION_GUIDANCE | bus0_media_out |
| oem_strategy_2 | 16 | NAVIGATION_GUIDANCE | bus0_media_out |
| radio | 17 | MEDIA (car_audio_type=3) | bus0_media_out |
| ext_audio_source | 18 | MEDIA (car_audio_type=7) | bus0_media_out |
| voice_command | 19 | ASSISTANT, ACCESSIBILITY | bus0_media_out |
| safety_alert | 20 | NOTIFICATION (car_audio_type=2) | bus0_media_out |
| music | 21 | MEDIA, GAME | bus0_media_out |
| nav_guidance | 22 | NAVIGATION_GUIDANCE | bus0_media_out |
| voice_call | 23 | VOICE_COMMUNICATION | bus0_media_out |
| alarm | 24 | ALARM | bus0_media_out |
| ring | 25 | TELEPHONY_RINGTONE | bus0_media_out |
| notification | 26 | NOTIFICATION | bus0_media_out |
| system | 27 | SONIFICATION | bus0_media_out |
| tts | 28 | TTS | bus0_media_out |

---

## Audio Routing (Audio Patches)

Active audio patches route mixer outputs to bus devices:

| Patch | Source | Sink |
|-------|--------|------|
| 1 | Mix ID 1 (handle 13) | bus0_media_out |
| 2 | Mix ID 5 (handle 21) | bus1_navigation_out |
| 3 | Mix ID 8 (handle 29) | bus2_voice_command_out |
| 4 | Mix ID 11 (handle 37) | bus3_call_ring_out |
| 5 | Mix ID 14 (handle 45) | bus4_call_out |
| 6 | Mix ID 17 (handle 53) | bus5_alarm_out |
| 7 | Mix ID 20 (handle 61) | bus6_notification_out |
| 8 | Mix ID 23 (handle 69) | bus7_system_sound_out |
| 9 | Mix ID 26 (handle 77) | bus8_ecall_ring_out |
| 10 | Mix ID 29 (handle 85) | bus11_mix_unduck_out |
| 11 | Mix ID 32 (handle 93) | bus12_audio_cue_out |
| 12 | Mix ID 35 (handle 101) | bus13_high_priority_mutex_out |

---

## Audio Focus Management

### Focus Policy

| Property | Value |
|----------|-------|
| External Focus Policy | Enabled |
| Multi Audio Focus | Disabled |
| Focus Owner | com.gm.gmaudio.tuner |

### Audio Attributes to Bus Mapping

| Audio Usage | Destination Bus |
|-------------|-----------------|
| USAGE_MEDIA | bus0_media_out |
| USAGE_GAME | bus0_media_out |
| USAGE_ASSISTANCE_NAVIGATION_GUIDANCE | bus1_navigation_out |
| USAGE_ASSISTANT | bus2_voice_command_out |
| USAGE_ASSISTANCE_ACCESSIBILITY | bus2_voice_command_out |
| USAGE_NOTIFICATION_TELEPHONY_RINGTONE | bus3_call_ring_out |
| USAGE_VOICE_COMMUNICATION | bus4_call_out |
| USAGE_ALARM | bus5_alarm_out |
| USAGE_NOTIFICATION | bus6_notification_out |
| USAGE_ASSISTANCE_SONIFICATION | bus7_system_sound_out |

---

## Performance Metrics

### Output Statistics (Primary)

| Metric | Value |
|--------|-------|
| Total Writes | 17,405 |
| Delayed Writes | 0 |
| Underruns | 0 |
| Overruns | 0 |
| Frames Written | 13,367,040 |

### Jitter Statistics

| Metric | Value |
|--------|-------|
| Average | 0.028 ms |
| Std Dev | 0.181 ms |
| Min | -1.165 ms |
| Max | 1.186 ms |

---

## Recommendations

### CarPlay/Android Auto Audio

| Parameter | Recommended |
|-----------|-------------|
| Sample Rate | 48000 Hz |
| Format | PCM_16_BIT |
| Channels | Stereo |
| Audio Usage | USAGE_MEDIA |
| Destination | bus0_media_out |

### Voice Assistant Integration

| Parameter | Recommended |
|-----------|-------------|
| Sample Rate | 16000 or 48000 Hz |
| Format | PCM_16_BIT |
| Input Device | Built-In Mic |
| Audio Usage | USAGE_ASSISTANT |
| Effects | AEC + NS (Harman) |

### Low-Latency Audio

| Metric | Value |
|--------|-------|
| Min Latency | ~24 ms |
| Mix Period | 8 ms |
| Buffer Size | 384 frames (8 ms @ 48kHz) |
