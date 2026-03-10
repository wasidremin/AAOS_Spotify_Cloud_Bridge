# GM Infotainment System Documentation

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 2025 - January 2026

---

## Document Index

### Top-Level Documents

| Document | Description |
|----------|-------------|
| **[projection_comparison.md](projection_comparison.md)** | **CarPlay vs Android Auto comparison (video, audio, protocol)** |
| **[third_party_access.md](third_party_access.md)** | **Third-party app access to Intel Video/Audio APIs** |
| [hardware_rendering.md](hardware_rendering.md) | GPU, OpenGL, Vulkan specifications |

### Intel Media SDK Documentation (Video)

| Document | Description |
|----------|-------------|
| **[intel_media_sdk/README.md](intel_media_sdk/README.md)** | **Intel MFX SDK architecture, API reference, GM AAOS implementation** |
| [intel_media_sdk/mediasdk-man.pdf](intel_media_sdk/mediasdk-man.pdf) | Official Intel API Reference (offline, 1.4 MB) |
| [intel_media_sdk/intel-media-developers-guide.pdf](intel_media_sdk/intel-media-developers-guide.pdf) | Official Intel Developer's Guide (offline, 4.4 MB) |

### Intel Audio Subsystem Documentation

| Document | Description |
|----------|-------------|
| **[intel_audio/README.md](intel_audio/README.md)** | **Intel IAS SmartX + SST architecture, API analysis (no official docs)** |

### Video Documentation

| Document | Description |
|----------|-------------|
| [video/README.md](video/README.md) | Video subsystem overview |
| [video/carplay_video_pipeline.md](video/carplay_video_pipeline.md) | CarPlay/AirPlay video processing (CINEMO) |
| [video/h264_nal_processing.md](video/h264_nal_processing.md) | H.264 NAL unit processing |
| [video/cinemo_nme_framework.md](video/cinemo_nme_framework.md) | CINEMO/NME framework architecture |
| [video/video_codecs.md](video/video_codecs.md) | Video codec specifications |
| [video/display_subsystem.md](video/display_subsystem.md) | Display panel and SurfaceFlinger |
| [video/software_rendering.md](video/software_rendering.md) | CPU-based rendering |

### Audio Documentation

| Document | Description |
|----------|-------------|
| [audio/README.md](audio/README.md) | Audio subsystem overview |
| [audio/carplay_audio_pipeline.md](audio/carplay_audio_pipeline.md) | CarPlay/AirPlay audio processing (CINEMO) |
| [audio/audio_subsystem.md](audio/audio_subsystem.md) | Core audio system, AudioFlinger |
| [audio/audio_codecs.md](audio/audio_codecs.md) | Audio codec specifications |
| [audio/audio_effects.md](audio/audio_effects.md) | Harman preprocessing, NXP effects |
| [audio/automotive_audio.md](audio/automotive_audio.md) | AAOS multi-zone architecture |

---

## Hardware Summary

| Component | Specification |
|-----------|---------------|
| CPU | Intel IoT CPU 1.0, 4 cores @ 1.88 GHz |
| GPU | Intel HD Graphics 505 (Apollo Lake) |
| Display | 2400x960 @ 60Hz (DD134IA-01B) |
| OpenGL ES | 3.2 (Mesa 21.1.5) |
| Vulkan | 1.0.64 (Broxton driver) |
| Video Accel | Intel Media SDK (MFX) + VA-API + i965 driver |
| Audio Stack | Intel IAS SmartX + SST + Harman HAL |
| Audio Transport | AVB (Ethernet) to TDF8532 codec |

---

## Projection Support

### CarPlay

| Aspect | Implementation |
|--------|----------------|
| Framework | CINEMO/NME (Harman/Samsung) |
| Video Decoder | NVDEC Software (libNmeVideoSW.so) |
| Protocol | AirPlay 320.17.8 |
| Authentication | Apple MFi (iAP2) |
| Transport | USB NCM + IPv6, WiFi |

### Android Auto

| Aspect | Implementation |
|--------|----------------|
| Framework | Standard Android AOSP |
| Video Decoder | Intel Hardware (OMX.Intel.hw_vd.h264) |
| Protocol | Android Auto Protocol (AAP) |
| Authentication | Google certificates |
| Transport | USB AOA, WiFi |

See [projection_comparison.md](projection_comparison.md) for detailed analysis.

---

## Key Findings

### CarPlay Architecture
- Uses CINEMO framework separate from Android MediaCodec
- Software video decode despite hardware availability
- Custom AirPlay protocol integration
- Dedicated audio tuning (SCD files)
- ~17.5 MB of NME native libraries

### Android Auto Architecture
- Uses standard Android AOSP components
- Hardware-accelerated video decode
- Standard MediaCodec/AudioFlinger path
- Lower latency than CarPlay

### Documentation Gaps

**Android Auto Resolution Configuration:**
- No explicit video/UI resolution configuration found in extracted partitions
- Display is non-standard 2400x960 (2.5:1 aspect ratio)
- May cause aspect ratio mismatch or touch mapping issues
- See [projection_comparison.md](projection_comparison.md) for details

### Third-Party App Access

| API | Access | Method |
|-----|--------|--------|
| Intel Video (MFX) | **YES** | Standard MediaCodec API |
| Intel Audio (IAS SmartX) | **NO** | Below HAL barrier |

See [third_party_access.md](third_party_access.md) for code examples and details.

### Shared Components
- AudioFlinger with 12+ output buses
- Harman audio preprocessing (AEC/NS/AGC)
- SurfaceFlinger display composition
- PulseAudio + AVB to amplifier

---

## Data Sources

All specifications obtained from GM AAOS research data:

**ADB Enumeration:**
- dumpsys (SurfaceFlinger, audio, media.player, gpu)
- Process/service enumeration
- System properties

**Extracted Partitions:**
- `/vendor/etc/` - Configuration files
- `/system/lib64/` - Native libraries
- `/system/app/` - APKs

**Binary Analysis:**
- `strings`, `readelf`, `nm` on NME libraries

**Source:** `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`
