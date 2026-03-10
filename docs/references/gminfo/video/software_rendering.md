# GM Infotainment Software Rendering Specifications

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Software Rendering Overview

Software rendering on this device is handled by the Android Codec2 (C2) framework and legacy OMX components. These are CPU-based codecs that serve as fallbacks when hardware codecs are unavailable or incompatible.

---

## Software Video Decoders

### Codec2 (C2) Software Decoders

These are the primary software decoders with rank 512 (lower than hardware rank 256).

| Codec Name | MIME Type | Max Resolution | Max Bitrate | Profiles |
|------------|-----------|----------------|-------------|----------|
| `c2.android.avc.decoder` | video/avc | 4080x4080 | 48 Mbps | Baseline, Main, High, ConstrainedBaseline/High @ Level 5.2 |
| `c2.android.hevc.decoder` | video/hevc | 4096x4096 | 10 Mbps | Main, MainStill @ High 5.2 |
| `c2.android.vp8.decoder` | video/x-vnd.on2.vp8 | 2048x2048 | 40 Mbps | Main @ V0 |
| `c2.android.vp9.decoder` | video/x-vnd.on2.vp9 | 2048x2048 | 40 Mbps | 0/2/2HDR/2HDRPlus @ Level 5 |
| `c2.android.h263.decoder` | video/3gpp | 352x288 | 384 Kbps | Baseline, ISWV2 @ Level 40/45 |
| `c2.android.mpeg4.decoder` | video/mp4v-es | 2048x2048 | 384 Kbps | Simple @ Level 6 |
| `c2.android.av1.decoder` | video/av01 | 2048x2048 | 120 Mbps | Main8, Main10HDR10, Main10HDRPlus @ 5.3 |

### Legacy OMX Software Decoders

Fallback decoders with rank 528 (lowest priority).

| Codec Name | MIME Type | Max Resolution | Notes |
|------------|-----------|----------------|-------|
| `OMX.google.h264.decoder` | video/avc | 4080x4080 | Legacy fallback |
| `OMX.google.hevc.decoder` | video/hevc | 4096x4096 | Legacy fallback |
| `OMX.google.vp8.decoder` | video/x-vnd.on2.vp8 | 2048x2048 | Legacy fallback |
| `OMX.google.vp9.decoder` | video/x-vnd.on2.vp9 | 2048x2048 | Legacy fallback |
| `OMX.google.h263.decoder` | video/3gpp | 352x288 | Legacy fallback |
| `OMX.google.mpeg4.decoder` | video/mp4v-es | 2048x2048 | Legacy fallback |

---

## Software Video Encoders

### Codec2 (C2) Software Encoders

| Codec Name | MIME Type | Max Resolution | Max Bitrate | Features |
|------------|-----------|----------------|-------------|----------|
| `c2.android.avc.encoder` | video/avc | 2048x2048 | 12 Mbps | Intra-refresh |
| `c2.android.hevc.encoder` | video/hevc | 512x512 | 4 Mbps | Limited resolution |
| `c2.android.vp8.encoder` | video/x-vnd.on2.vp8 | 2048x2048 | 40 Mbps | VBR, CBR |
| `c2.android.vp9.encoder` | video/x-vnd.on2.vp9 | 2048x2048 | 40 Mbps | VBR, CBR |
| `c2.android.h263.encoder` | video/3gpp | 352x288 | 2 Mbps | - |
| `c2.android.mpeg4.encoder` | video/mp4v-es | 352x288 | 2 Mbps | - |

### Legacy OMX Software Encoders

| Codec Name | MIME Type | Max Resolution | Max Bitrate |
|------------|-----------|----------------|-------------|
| `OMX.google.h264.encoder` | video/avc | 2048x2048 | 12 Mbps |
| `OMX.google.vp8.encoder` | video/x-vnd.on2.vp8 | 2048x2048 | 40 Mbps |
| `OMX.google.vp9.encoder` | video/x-vnd.on2.vp9 | 2048x2048 | 40 Mbps |
| `OMX.google.h263.encoder` | video/3gpp | 176x144 | 128 Kbps |
| `OMX.google.mpeg4.encoder` | video/mp4v-es | 176x144 | 64 Kbps |

---

## Software Audio Decoders

### Codec2 (C2) Audio Decoders

| Codec Name | MIME Type | Max Channels | Sample Rates | Bitrate Range |
|------------|-----------|--------------|--------------|---------------|
| `c2.android.aac.decoder` | audio/mp4a-latm | 8 | 7.35-48 kHz | 8-960 Kbps |
| `c2.android.mp3.decoder` | audio/mpeg | 2 | 8-48 kHz | 8-320 Kbps |
| `c2.android.opus.decoder` | audio/opus | 8 | 8-48 kHz | 6-510 Kbps |
| `c2.android.vorbis.decoder` | audio/vorbis | 8 | 8-96 kHz | 32-500 Kbps |
| `c2.android.flac.decoder` | audio/flac | 8 | 1-655.35 kHz | 1-21 Mbps |
| `c2.android.amrnb.decoder` | audio/3gpp | 1 | 8 kHz | 4.75-12.2 Kbps |
| `c2.android.amrwb.decoder` | audio/amr-wb | 1 | 16 kHz | 6.6-23.85 Kbps |
| `c2.android.g711.alaw.decoder` | audio/g711-alaw | 6 | 8-48 kHz | 64 Kbps |
| `c2.android.g711.mlaw.decoder` | audio/g711-mlaw | 6 | 8-48 kHz | 64 Kbps |
| `c2.android.raw.decoder` | audio/raw | 8 | 8-96 kHz | 1-10 Mbps |

### AAC Profiles Supported

| Profile ID | Profile Name |
|------------|--------------|
| 2 | LC (Low Complexity) |
| 5 | HE (High Efficiency) |
| 29 | HE_PS (HE with Parametric Stereo) |
| 23 | LD (Low Delay) |
| 39 | ELD (Enhanced Low Delay) |
| 20 | ERScalable |
| 42 | XHE (Extended HE) |

---

## Software Audio Encoders

| Codec Name | MIME Type | Max Channels | Sample Rates | Bitrate Range | Modes |
|------------|-----------|--------------|--------------|---------------|-------|
| `c2.android.aac.encoder` | audio/mp4a-latm | 6 | 8-48 kHz | 8-512 Kbps | - |
| `c2.android.amrnb.encoder` | audio/3gpp | 1 | 8 kHz | 4.75-12.2 Kbps | CBR |
| `c2.android.amrwb.encoder` | audio/amr-wb | 1 | 16 kHz | 6.6-23.85 Kbps | CBR |
| `c2.android.flac.encoder` | audio/flac | 2 | 1-655.35 kHz | 1-21 Mbps | CQ |
| `c2.android.opus.encoder` | audio/opus | 2 | 8-48 kHz | 6-510 Kbps | CBR, VBR |

---

## Color Format Support

### Supported YUV Formats

| Format Code | Format Name | Description |
|-------------|-------------|-------------|
| 0x7f420888 | YUV420Flexible | Platform-optimal YUV 4:2:0 |
| 0x13 | YUV420Planar | I420 (Y, U, V planes) |
| 0x15 | YUV420SemiPlanar | NV12 (Y plane, UV interleaved) |
| 0x14 | YUV420PackedPlanar | Packed I420 variant |
| 0x27 | YUV420PackedSemiPlanar | Packed NV12 variant |

---

## Software Decoder Performance

### Measured Frame Rates (CPU-based decoding)

**H.264 Software Decode (c2.android.avc.decoder):**
| Resolution | FPS Range |
|------------|-----------|
| 320x240 | 150-180 |
| 720x480 | 60-80 |
| 1280x720 | 25-30 |
| 1920x1080 | 11-13 |

**H.265 Software Decode (c2.android.hevc.decoder):**
| Resolution | FPS Range |
|------------|-----------|
| 352x288 | 170-200 |
| 640x360 | 95-115 |
| 720x480 | 85-105 |
| 1280x720 | 43-53 |
| 1920x1080 | 27-32 |

**VP8 Software Decode (c2.android.vp8.decoder):**
| Resolution | FPS Range |
|------------|-----------|
| 320x180 | 255-285 |
| 640x360 | 105-125 |
| 1280x720 | 45-70 |
| 1920x1080 | 11-16 |

**VP9 Software Decode (c2.android.vp9.decoder):**
| Resolution | FPS Range |
|------------|-----------|
| 320x180 | 235-265 |
| 640x360 | 117-137 |
| 1280x720 | 44-64 |
| 1920x1080 | 32-37 |

**AV1 Software Decode (c2.android.av1.decoder):**
- Maximum resolution: 2048x2048
- No measured frame rates available (limited use case)

---

## Codec Priority and Selection

### Decoder Priority (Lower = Higher Priority)

| Rank | Owner | Example |
|------|-------|---------|
| 256 | vendor/hardware | `OMX.Intel.hw_vd.h264` |
| 512 | codec2::software | `c2.android.avc.decoder` |
| 528 | default/OMX | `OMX.google.h264.decoder` |

### Codec Selection Logic

1. **Hardware codecs** (rank 256) are preferred for supported formats
2. **Codec2 software** (rank 512) used when hardware unavailable
3. **Legacy OMX** (rank 528) as final fallback

### Attributes Reference

| Attribute | Bit | Meaning |
|-----------|-----|---------|
| encoder | 0x1 | Codec is an encoder |
| vendor | 0x2 | Vendor-provided codec |
| software-only | 0x4 | No hardware acceleration |
| hw-accelerated | 0x8 | Uses hardware acceleration |

---

## Software Rendering Use Cases

### When Software Decoding is Used

1. **Unsupported Profiles:** Hardware doesn't support specific codec profile
2. **Resolution Limits:** Content exceeds hardware capabilities
3. **Secure Path Unavailable:** DRM content without secure hardware
4. **Format Compatibility:** Rare formats like AV1 (no hardware support)
5. **Fallback:** Hardware decoder busy or failed

### AV1 Support (Software Only)

The device supports AV1 decoding via software only:
- Decoder: `c2.android.av1.decoder`
- Max Resolution: 2048x2048
- Profiles: Main8, Main10HDR10, Main10HDRPlus @ Level 5.3
- **Note:** No hardware acceleration available

---

## RenderEngine (GPU Composition)

### Software Fallback Composition

When hardware composition is unavailable, SurfaceFlinger uses RenderEngine:

| Property | Value |
|----------|-------|
| Engine Type | SkiaGL (OpenGL ES) |
| Protected Context | Not Supported |
| Program Cache Size | 3 programs |
| Image Cache Size | 27 images |
| Framebuffer Cache | 3 buffers |

### Composition Path

```
Layer Buffer → RenderEngine (GPU) → Framebuffer → Display
```

vs Hardware Path:
```
Layer Buffer → HWC Overlay → Display
```

---

## Memory and Buffer Management

### Software Codec Buffer Configuration

| Codec Type | Input Buffers | Output Buffers |
|------------|---------------|----------------|
| Video Decoder | 8-16 | 8-12 |
| Video Encoder | 9 (max) | 8 |
| Audio Decoder | 4 | 4 |
| Audio Encoder | 4 | 4 |

### System Setting
```xml
<Setting name="max-video-encoder-input-buffers" value="9" />
```

---

## Recommendations

### When to Use Software Codecs

1. **AV1 Content:** Only software decoder available
2. **High-Resolution Thumbnails:** Quick decode without occupying hardware
3. **Background Transcoding:** Non-real-time operations
4. **Compatibility Testing:** Verify content without hardware dependencies

### Performance Considerations

- Software decoding at 1080p achieves only 11-32 fps (depending on codec)
- Use hardware codecs for real-time playback above 480p
- Software encoders suitable for offline processing only
- CPU cores will be heavily utilized during software decode

### Power Impact

Software decoding significantly increases:
- CPU utilization (all 4 cores may be engaged)
- Power consumption (vs hardware decode)
- Thermal output
- Battery drain (if applicable)

Always prefer hardware codecs for automotive use cases.
