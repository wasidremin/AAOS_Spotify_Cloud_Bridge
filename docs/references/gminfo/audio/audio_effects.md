# GM Infotainment Audio Effects Specifications

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Audio Effects Overview

The GM infotainment system includes a comprehensive set of audio effects for preprocessing (microphone input) and post-processing (speaker output). Notable is the Harman audio preprocessing library customized for GM.

---

## Effect Libraries

### Loaded Libraries

| Library Name | Path | Description |
|--------------|------|-------------|
| pre_processing | `/vendor/lib64/soundfx/libharmanpreprocessing_gm.so` | Harman GM-specific preprocessing |
| bundle | `/vendor/lib64/soundfx/libbundlewrapper.so` | NXP effects bundle |
| reverb | `/vendor/lib64/soundfx/libreverbwrapper.so` | NXP reverb effects |
| visualizer | `/vendor/lib64/soundfx/libvisualizer.so` | AOSP audio visualizer |
| downmix | `/vendor/lib64/soundfx/libdownmix.so` | AOSP multichannel downmix |
| loudness_enhancer | `/vendor/lib64/soundfx/libldnhncr.so` | AOSP loudness enhancement |
| dynamics_processing | `/vendor/lib64/soundfx/libdynproc.so` | AOSP dynamics processing |

### Additional Libraries (Present but not loaded by default)

| Library | Path | Purpose |
|---------|------|---------|
| libaudiopreprocessing.so | /vendor/lib64/soundfx/ | Generic AOSP preprocessing |
| libeffectproxy.so | /vendor/lib64/soundfx/ | Effect proxy for offload |
| libhapticgenerator.so | /vendor/lib64/soundfx/ | Haptic feedback generation |
| liblpepreprocessing.so | /vendor/lib64/soundfx/ | LPE preprocessing |

---

## Preprocessing Effects (Input)

### Harman Audio Effects (GM Custom)

These effects are specifically tuned for the GM vehicle cabin acoustics.

#### Noise Suppression (NS)

| Property | Value |
|----------|-------|
| Name | Noise Suppression |
| Vendor | Harman Audio Effects |
| UUID | 1d97bb0b-9e2f-4403-9ae3-58c2554306f8 |
| Type UUID | 58b4b260-8e06-11e0-aa8e-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00020203 |

**Purpose:** Reduces background noise from road, wind, engine, and HVAC.

---

#### Acoustic Echo Canceler (AEC)

| Property | Value |
|----------|-------|
| Name | Acoustic Echo Canceler |
| Vendor | Harman Audio Effects |
| UUID | 0f8d0d2a-59e5-45fe-b6e4-248c8a799109 |
| Type UUID | 7b491460-8d4d-11e0-bd61-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00020203 |

**Purpose:** Removes speaker audio from microphone input to prevent echo during calls.

---

#### Automatic Gain Control (AGC)

| Property | Value |
|----------|-------|
| Name | Automatic Gain Control |
| Vendor | Harman Audio Effects |
| UUID | 0dd49521-8c59-40b1-b403-e08d5f01875e |
| Type UUID | 0a8abfe0-654c-11e0-ba26-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00020203 |

**Purpose:** Normalizes microphone volume regardless of speaker distance.

---

### Preprocessing Configuration

Effects are automatically applied based on audio source:

| Stream Type | Applied Effects |
|-------------|-----------------|
| voice_communication | AEC, NS |
| voice_recognition | AEC, NS |
| camcorder | (none) |
| mic | (none) |

---

## Post-Processing Effects (Output)

### NXP Effects Bundle

#### Dynamic Bass Boost

| Property | Value |
|----------|-------|
| Name | Dynamic Bass Boost |
| Vendor | NXP Software Ltd. |
| UUID | 8631f300-72e2-11df-b57e-0002a5d5c51b |
| Type UUID | 0634f220-ddd4-11db-a0fc-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000248 |

**Purpose:** Enhances low-frequency content for fuller bass response.

---

#### Virtualizer

| Property | Value |
|----------|-------|
| Name | Virtualizer |
| Vendor | NXP Software Ltd. |
| UUID | 1d4033c0-8557-11df-9f2d-0002a5d5c51b |
| Type UUID | 37cc2c00-dddd-11db-8577-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000250 |

**Purpose:** Creates spatial audio effect for headphone/speaker listening.

---

#### Equalizer

| Property | Value |
|----------|-------|
| Name | Equalizer |
| Vendor | NXP Software Ltd. |
| UUID | ce772f20-847d-11df-bb17-0002a5d5c51b |
| Type UUID | 0bed4300-ddd6-11db-8f34-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000048 |

**Purpose:** Multi-band frequency adjustment.

---

#### Volume

| Property | Value |
|----------|-------|
| Name | Volume |
| Vendor | NXP Software Ltd. |
| UUID | 119341a0-8469-11df-81f9-0002a5d5c51b |
| Type UUID | 09e8ede0-ddde-11db-b4f6-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000050 |

**Purpose:** Software volume control with smooth ramping.

---

### Reverb Effects (NXP)

#### Insert Preset Reverb

| Property | Value |
|----------|-------|
| Name | Insert Preset Reverb |
| Vendor | NXP Software Ltd. |
| UUID | 172cdf00-a3bc-11df-a72f-0002a5d5c51b |
| Type UUID | 47382d60-ddd8-11db-bf3a-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000048 |

---

#### Auxiliary Preset Reverb

| Property | Value |
|----------|-------|
| Name | Auxiliary Preset Reverb |
| Vendor | NXP Software Ltd. |
| UUID | f29a1400-a3bb-11df-8ddc-0002a5d5c51b |
| Type UUID | 47382d60-ddd8-11db-bf3a-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000001 |

---

#### Insert Environmental Reverb

| Property | Value |
|----------|-------|
| Name | Insert Environmental Reverb |
| Vendor | NXP Software Ltd. |
| UUID | c7a511a0-a3bb-11df-860e-0002a5d5c51b |
| Type UUID | c2e5d5f0-94bd-4763-9cac-4e234d06839e |
| API Version | 2.0 |
| Flags | 0x00000048 |

---

#### Auxiliary Environmental Reverb

| Property | Value |
|----------|-------|
| Name | Auxiliary Environmental Reverb |
| Vendor | NXP Software Ltd. |
| UUID | 4a387fc0-8ab3-11df-8bad-0002a5d5c51b |
| Type UUID | c2e5d5f0-94bd-4763-9cac-4e234d06839e |
| API Version | 2.0 |
| Flags | 0x00000001 |

---

### AOSP Effects

#### Multichannel Downmix

| Property | Value |
|----------|-------|
| Name | Multichannel Downmix To Stereo |
| Vendor | The Android Open Source Project |
| UUID | 93f04452-e4fe-41cc-91f9-e475b6d1d69f |
| Type UUID | 381e49cc-a858-4aa2-87f6-e8388e7601b2 |
| API Version | 2.0 |
| Flags | 0x00000008 |

**Purpose:** Converts multichannel audio (5.1, 7.1) to stereo output.

---

#### Visualizer

| Property | Value |
|----------|-------|
| Name | Visualizer |
| Vendor | The Android Open Source Project |
| UUID | d069d9e0-8329-11df-9168-0002a5d5c51b |
| Type UUID | e46b26a0-dddd-11db-8afd-0002a5d5c51b |
| API Version | 2.0 |
| Flags | 0x00000008 |

**Purpose:** Provides FFT and waveform data for audio visualization.

---

#### Loudness Enhancer

| Property | Value |
|----------|-------|
| Name | Loudness Enhancer |
| Vendor | The Android Open Source Project |
| UUID | fa415329-2034-4bea-b5dc-5b381c8d1e2c |
| Type UUID | fe3199be-aed0-413f-87bb-11260eb63cf1 |
| API Version | 2.0 |
| Flags | 0x00000008 |

**Purpose:** Increases perceived loudness without clipping.

---

#### Dynamics Processing

| Property | Value |
|----------|-------|
| Name | Dynamics Processing |
| Vendor | The Android Open Source Project |
| UUID | e0e6539b-1781-7261-676f-6d7573696340 |
| Type UUID | 7261676f-6d75-7369-6364-28e2fd3ac39e |
| API Version | 2.0 |
| Flags | 0x00000050 |

**Purpose:** Multi-band dynamics processing (compression, limiting, expansion).

---

## Effect Types Reference

### Effect Type UUIDs

| Type | UUID | Description |
|------|------|-------------|
| Bass Boost | 0634f220-ddd4-11db-a0fc-0002a5d5c51b | Low frequency enhancement |
| Equalizer | 0bed4300-ddd6-11db-8f34-0002a5d5c51b | Frequency equalization |
| Virtualizer | 37cc2c00-dddd-11db-8577-0002a5d5c51b | Spatial audio |
| Volume | 09e8ede0-ddde-11db-b4f6-0002a5d5c51b | Volume control |
| Reverb (Preset) | 47382d60-ddd8-11db-bf3a-0002a5d5c51b | Preset reverb |
| Reverb (Environmental) | c2e5d5f0-94bd-4763-9cac-4e234d06839e | Environmental reverb |
| AEC | 7b491460-8d4d-11e0-bd61-0002a5d5c51b | Echo cancellation |
| AGC | 0a8abfe0-654c-11e0-ba26-0002a5d5c51b | Gain control |
| NS | 58b4b260-8e06-11e0-aa8e-0002a5d5c51b | Noise suppression |
| Visualizer | e46b26a0-dddd-11db-8afd-0002a5d5c51b | Audio visualization |
| Downmix | 381e49cc-a858-4aa2-87f6-e8388e7601b2 | Channel downmix |
| Loudness | fe3199be-aed0-413f-87bb-11260eb63cf1 | Loudness enhancement |
| Dynamics | 7261676f-6d75-7369-6364-28e2fd3ac39e | Dynamics processing |

---

## Effect Flags Reference

| Flag | Value | Meaning |
|------|-------|---------|
| INSERT | 0x0001 | Insert effect (in-line) |
| AUXILIARY | 0x0002 | Auxiliary effect (mix) |
| PROCESS_FORWARD | 0x0004 | Forward processing |
| PROCESS_REVERSE | 0x0008 | Reverse processing |
| TYPE_INSERT | 0x0000 | Insert type |
| TYPE_AUXILIARY | 0x0001 | Auxiliary type |
| TYPE_PRE_PROC | 0x0002 | Pre-processor type |
| TYPE_POST_PROC | 0x0003 | Post-processor type |
| HW_ACC | 0x0008 | Hardware accelerated |
| HW_ACC_TUNNEL | 0x0010 | Hardware tunnel |
| NO_PROCESS | 0x0040 | No processing needed |
| VOLUME_CTRL | 0x0080 | Volume control effect |

---

## Usage Recommendations

### Voice Call Processing

For optimal voice call quality:

```
Input Chain:
  Microphone → AGC → NS → AEC → Voice Encoder

Output Chain:
  Voice Decoder → Volume → Speaker
```

### Voice Assistant

For voice recognition accuracy:

```
Input Chain:
  Microphone → NS → AEC → Voice Recognizer
```

### Music Playback

For enhanced music playback:

```
Output Chain:
  Audio Decoder → Equalizer → Bass Boost →
  Virtualizer → Loudness Enhancer → Volume → Speaker
```

---

## Current Effect Usage

| Effect | Current CPU (MIPS) | Memory (KB) |
|--------|-------------------|-------------|
| Total | 0.00 | 0 |

**Note:** Effects are loaded on-demand and unloaded when not in use.

---

## Integration Notes

### For CarPlay/Android Auto

1. **AEC Reference:** Echo reference port available for proper echo cancellation
2. **Preprocessing:** Harman effects automatically applied for voice_communication
3. **Downmix:** Multichannel content automatically downmixed to stereo

### For Voice Assistants

1. Use `AUDIO_SOURCE_VOICE_RECOGNITION` for optimal preprocessing
2. AEC and NS automatically enabled
3. AGC helps maintain consistent voice levels

### For Media Playback

1. Effects can be applied per AudioTrack
2. Equalizer presets available via AudioEffect API
3. Visualizer requires special permission
