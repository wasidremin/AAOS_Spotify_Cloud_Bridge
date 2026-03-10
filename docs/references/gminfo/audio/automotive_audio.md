# GM Infotainment Automotive Audio Architecture

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Android Automotive Audio Overview

This system implements the Android Automotive OS (AAOS) multi-zone audio architecture, providing dedicated audio buses for different audio contexts. This enables sophisticated audio mixing and ducking managed by an external DSP/amplifier.

---

## Audio Bus Architecture

### Output Bus Topology

```
┌─────────────────────────────────────────────────────────────────┐
│                     Android AudioFlinger                         │
├─────────────────────────────────────────────────────────────────┤
│  ┌──────────────┐  ┌──────────────┐  ┌──────────────┐           │
│  │ Media Mix    │  │ Nav Mix      │  │ Voice Mix    │  ...      │
│  │ (bus0)       │  │ (bus1)       │  │ (bus2)       │           │
│  └──────┬───────┘  └──────┬───────┘  └──────┬───────┘           │
└─────────┼──────────────────┼──────────────────┼─────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│                    Audio HAL (AUDIO_DEVICE_OUT_BUS)              │
└─────────────────────────────────────────────────────────────────┘
          │                  │                  │
          ▼                  ▼                  ▼
┌─────────────────────────────────────────────────────────────────┐
│              External DSP / Amplifier System                     │
│   ┌────────────────────────────────────────────────────────┐    │
│   │  Mixing │ Ducking │ EQ │ Gain │ Routing │ Fading       │    │
│   └────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────┘
                              │
                              ▼
                    ┌─────────────────┐
                    │    Speakers     │
                    └─────────────────┘
```

---

## Output Buses Detailed

### bus0_media_out (Primary)

| Property | Value |
|----------|-------|
| Address | bus0_media_out |
| Output ID | 13 |
| Flags | AUDIO_OUTPUT_FLAG_PRIMARY |
| Sample Rate | 8000, 16000, 24000, 48000 Hz |
| Channels | Mono, Stereo |
| Gain Range | 0 to +63 dB |
| Default Gain | +30 dB |

**Routed Audio Usages:**
- USAGE_MEDIA
- USAGE_GAME
- USAGE_UNKNOWN
- USAGE_EMERGENCY
- USAGE_SAFETY
- USAGE_VEHICLE_STATUS
- USAGE_ANNOUNCEMENT

---

### bus1_navigation_out

| Property | Value |
|----------|-------|
| Address | bus1_navigation_out |
| Output ID | 21 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_ASSISTANCE_NAVIGATION_GUIDANCE

---

### bus2_voice_command_out

| Property | Value |
|----------|-------|
| Address | bus2_voice_command_out |
| Output ID | 29 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_ASSISTANT
- USAGE_ASSISTANCE_ACCESSIBILITY

---

### bus3_call_ring_out

| Property | Value |
|----------|-------|
| Address | bus3_call_ring_out |
| Output ID | 37 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_NOTIFICATION_TELEPHONY_RINGTONE

---

### bus4_call_out

| Property | Value |
|----------|-------|
| Address | bus4_call_out |
| Output ID | 45 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_VOICE_COMMUNICATION
- USAGE_VOICE_COMMUNICATION_SIGNALLING

---

### bus5_alarm_out

| Property | Value |
|----------|-------|
| Address | bus5_alarm_out |
| Output ID | 53 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_ALARM

---

### bus6_notification_out

| Property | Value |
|----------|-------|
| Address | bus6_notification_out |
| Output ID | 61 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_NOTIFICATION
- USAGE_NOTIFICATION_COMMUNICATION_REQUEST
- USAGE_NOTIFICATION_COMMUNICATION_INSTANT
- USAGE_NOTIFICATION_COMMUNICATION_DELAYED
- USAGE_NOTIFICATION_EVENT

---

### bus7_system_sound_out

| Property | Value |
|----------|-------|
| Address | bus7_system_sound_out |
| Output ID | 69 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Routed Audio Usages:**
- USAGE_ASSISTANCE_SONIFICATION

---

### bus8_ecall_ring_out

| Property | Value |
|----------|-------|
| Address | bus8_ecall_ring_out |
| Output ID | 77 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Purpose:** Emergency call (eCall) ringtone - highest priority

---

### bus11_mix_unduck_out

| Property | Value |
|----------|-------|
| Address | bus11_mix_unduck_out |
| Output ID | 85 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Purpose:** Audio that should not be ducked during interruptions

---

### bus12_audio_cue_out

| Property | Value |
|----------|-------|
| Address | bus12_audio_cue_out |
| Output ID | 93 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Purpose:** System audio cues and earcons

---

### bus13_high_priority_mutex_out

| Property | Value |
|----------|-------|
| Address | bus13_high_priority_mutex_out |
| Output ID | 101 |
| Sample Rate | 48000 Hz |
| Channels | Stereo |
| Gain Range | 0 to +63 dB |

**Purpose:** High-priority mutually exclusive audio (safety alerts)

---

## Input Buses Detailed

### External Audio Sources

| Bus Address | Source | Sample Rate | Channels |
|-------------|--------|-------------|----------|
| bus3_sxm_in | SiriusXM satellite radio | 48 kHz | Stereo |
| bus4_lvm_in | Low Voltage Module | 48 kHz | Stereo |
| bus5_tcp_phone_dnlink_in | TCP phone downlink | 48 kHz | Stereo |
| bus6_tcp_prompt_in | TCP prompts | 48 kHz | Mono |
| bus7_bt_dnlink_in | Bluetooth A2DP downlink | 48 kHz | Stereo |
| bus8_tuner_in | FM tuner | 48 kHz | Stereo |
| bus9_tcp_phone_emerg_dnlink_in | Emergency phone | 48 kHz | Stereo |
| bus10_tuner_am_in | AM tuner | 48 kHz | Stereo |
| bus11_lvm_prompt_in | LVM prompts | 48 kHz | Mono |
| bus12_tcp_mixprompt_in | TCP mix prompts | 48 kHz | Mono |
| bus13_dab_in | DAB digital radio | 48 kHz | Stereo |
| bus14_rsi_in | RSI (Road Safety Info) | 48 kHz | Stereo |

---

## Audio Policy Strategies

### OEM Strategies

| Strategy | ID | Description | Volume Group |
|----------|----|--------------| -------------|
| oem_traffic_anouncement | 14 | Traffic announcements | 1 |
| oem_strategy_1 | 15 | OEM strategy 1 | 2 |
| oem_strategy_2 | 16 | OEM strategy 2 | 3 |
| radio | 17 | FM/AM/DAB radio | 4 |
| ext_audio_source | 18 | External audio source | 5 |
| voice_command | 19 | Voice assistant | 7 |
| safety_alert | 20 | Safety alerts | 8 |

### Standard Strategies

| Strategy | ID | Description | Volume Group |
|----------|----|--------------| -------------|
| music | 21 | Media playback | 6 |
| nav_guidance | 22 | Navigation | 7 |
| voice_call | 23 | Phone calls | 9 |
| alarm | 24 | Alarms | 10 |
| ring | 25 | Ringtones | 10 |
| notification | 26 | Notifications | 10 |
| system | 27 | System sounds | 8 |
| tts | 28 | Text-to-speech | 11 |

---

## Volume Groups

### OEM Volume Groups

| Group | ID | Mutable | Min | Max | Curve Points |
|-------|----|---------|-----|-----|--------------|
| oem_traffic_anouncement | 1 | Yes | 0 | 40 | -42, -28, -14, 0 dB |
| oem_adas_2 | 2 | Yes | 0 | 40 | -42, -28, -14, 0 dB |
| oem_adas_3 | 3 | Yes | 0 | 40 | -24, -16, -8, 0 dB |
| media_car_audio_type_3 | 4 | Yes | 0 | 40 | -42, -28, -14, 0 dB |
| media_car_audio_type_7 | 5 | Yes | 0 | 40 | -24, -16, -8, 0 dB |

### Standard Volume Groups

| Group | ID | Mutable | Min | Max | Associated Streams |
|-------|----|---------|-----|-----|-------------------|
| media | 6 | Yes | 0 | 40 | MUSIC |
| speech | 7 | Yes | 1 | 40 | ACCESSIBILITY |
| system | 8 | Yes | 0 | 40 | SYSTEM, DTMF, ENFORCED_AUDIBLE |
| phone | 9 | Yes | 0 | 40 | VOICE_CALL, BLUETOOTH_SCO |
| ring | 10 | Yes | 0 | 40 | ALARM, RING, NOTIFICATION |
| tts | 11 | Yes | 0 | 40 | TTS |

---

## Audio Focus Management

### Focus Configuration

| Property | Value |
|----------|-------|
| Multi Audio Focus | Disabled |
| External Focus Policy | Enabled |
| Focus Policy Owner | com.gm.gmaudio.tuner |

### Focus Priority (Implicit)

1. **Emergency (eCall)** - Highest priority, mutes all
2. **Phone Calls** - High priority, ducks media
3. **Navigation** - Ducks media, mixes with calls
4. **Voice Assistant** - Ducks media
5. **Alarms** - Ducks media
6. **Notifications** - Brief ducking
7. **Media** - Base priority

---

## Ducking Behavior

### Automatic Ducking

The external DSP handles ducking based on bus activity:

| Active Bus | Ducked Buses | Duck Level |
|------------|--------------|------------|
| bus8_ecall_ring_out | All others | Muted |
| bus4_call_out | bus0_media_out | -24 dB |
| bus1_navigation_out | bus0_media_out | -12 dB |
| bus2_voice_command_out | bus0_media_out | -12 dB |
| bus6_notification_out | bus0_media_out | -6 dB |

### Mix Unduck

Audio routed to `bus11_mix_unduck_out` is not ducked regardless of other active buses.

---

## CarPlay/Android Auto Integration

### Audio Routing

| CarPlay/AA Source | Android Usage | Destination Bus |
|-------------------|---------------|-----------------|
| Media | USAGE_MEDIA | bus0_media_out |
| Navigation | USAGE_ASSISTANCE_NAVIGATION_GUIDANCE | bus1_navigation_out |
| Voice Assistant | USAGE_ASSISTANT | bus2_voice_command_out |
| Phone Call | USAGE_VOICE_COMMUNICATION | bus4_call_out |
| Notification | USAGE_NOTIFICATION | bus6_notification_out |

### Microphone Routing

| CarPlay/AA Input | Android Source | Input Device |
|------------------|----------------|--------------|
| Voice Recognition | AUDIO_SOURCE_VOICE_RECOGNITION | Built-In Mic |
| Phone Call | AUDIO_SOURCE_VOICE_COMMUNICATION | mic_for_vc |

---

## Configuration Files

### Primary Configuration

```
/vendor/etc/audio_policy_configuration.xml
```

### Volumes Configuration

```
/vendor/etc/audio_policy_volumes.xml
```

### Effects Configuration

```
/vendor/etc/audio_effects.xml
```

---

## System Services

### Running Services

| Service | Status | Purpose |
|---------|--------|---------|
| audioserver | running | Core audio services |
| vehicleaudiocontrol | running | Vehicle audio control |

### Stopped Services

| Service | Status | Purpose |
|---------|--------|---------|
| earlyaudioalsa | stopped | Early audio (boot sounds) |
| rtpolicy_pulseaudio | stopped | PulseAudio policy |

---

## Recommendations for Developers

### For CarLink Audio

1. **Media Playback:**
   - Use `AudioAttributes.USAGE_MEDIA`
   - Use `AudioAttributes.CONTENT_TYPE_MUSIC`
   - Route to `bus0_media_out` automatically

2. **Navigation Audio:**
   - Use `AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE`
   - Will duck media automatically via DSP
   - Route to `bus1_navigation_out`

3. **Voice Assistant:**
   - Use `AudioAttributes.USAGE_ASSISTANT`
   - Request `AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK`
   - Route to `bus2_voice_command_out`

4. **Phone Calls:**
   - Use `AudioAttributes.USAGE_VOICE_COMMUNICATION`
   - Request `AUDIOFOCUS_GAIN_TRANSIENT`
   - Route to `bus4_call_out`

### Audio Session Management

```kotlin
// Example: Request focus for media
val audioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_MEDIA)
    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
    .build()

val focusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
    .setAudioAttributes(audioAttributes)
    .setOnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> duck()
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> pause()
            AudioManager.AUDIOFOCUS_LOSS -> stop()
            AudioManager.AUDIOFOCUS_GAIN -> play()
        }
    }
    .build()

audioManager.requestAudioFocus(focusRequest)
```
