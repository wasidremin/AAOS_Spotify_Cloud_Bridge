# GM Infotainment Video Codec Specifications

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Research Date:** December 7, 2025

---

## Codec Overview

This document provides detailed specifications for all video codecs available on the GM infotainment system, including hardware-accelerated (Intel OMX) and software (Android C2/OMX) implementations.

---

## Hardware Video Decoders (Intel)

### H.264/AVC Decoder

| Property | `OMX.Intel.hw_vd.h264` |
|----------|------------------------|
| MIME Type | video/avc |
| Owner | default (vendor) |
| Rank | 256 (highest priority) |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |
| Block Size | 16x16 macroblocks |
| Blocks/Second | 1 - 972,000 |
| Alignment | 2x2 pixels |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Baseline | 5.1 |
| Constrained Baseline | 5.1 |
| Main | 5.1 |
| Extended | 5.1 |
| High | 5.1 |
| Constrained High | 5.1 |

**Color Formats:**
- YUV420Flexible (0x7f420888)
- YUV420SemiPlanar (0x15)
- Implementation-defined (0x100)

**Features:**
- Adaptive playback
- Performance point: 3840x2160 @ 60 fps

**Measured Performance:**
| Resolution | Frame Rate |
|------------|------------|
| 320x240 | 740-980 fps |
| 720x480 | 830-1020 fps |
| 1280x720 | 460-590 fps |
| 1920x1088 | 320-360 fps |

---

### H.264/AVC Secure Decoder

| Property | `OMX.Intel.hw_vd.h264.secure` |
|----------|-------------------------------|
| MIME Type | video/avc |
| Features | Secure playback (DRM) |
| All other specs | Same as non-secure variant |

---

### H.265/HEVC Decoder

| Property | `OMX.Intel.hw_vd.h265` |
|----------|------------------------|
| MIME Type | video/hevc |
| Owner | default (vendor) |
| Rank | 256 |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |
| Block Size | 16x16 |
| Blocks/Second | 1 - 972,000 |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Main | Main 5.1 |
| Main10 | Main 5.1 |

**Features:**
- Adaptive playback
- Performance point: 3840x2160 @ 60 fps
- 10-bit HDR capable (Main10)

**Measured Performance:**
| Resolution | Frame Rate |
|------------|------------|
| 352x288 | 600-1000 fps |
| 640x360 | 450-800 fps |
| 720x480 | 400-700 fps |
| 1280x720 | 250-500 fps |
| 1920x1080 | 190-400 fps |
| 3840x2160 | 120-130 fps |

---

### H.265/HEVC Secure Decoder

| Property | `OMX.Intel.hw_vd.h265.secure` |
|----------|-------------------------------|
| MIME Type | video/hevc |
| Features | Secure playback (DRM) |
| All other specs | Same as non-secure variant |

---

### VP8 Decoder

| Property | `OMX.Intel.hw_vd.vp8` |
|----------|----------------------|
| MIME Type | video/x-vnd.on2.vp8 |
| Owner | default (vendor) |
| Rank | 256 |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Main | V0 |

**Measured Performance:**
| Resolution | Frame Rate |
|------------|------------|
| 320x180 | 650-1300 fps |
| 640x360 | 600-620 fps |
| 1280x720 | 200-400 fps |
| 1920x1080 | 150-330 fps |

---

### VP9 Decoder

| Property | `OMX.Intel.hw_vd.vp9` |
|----------|----------------------|
| MIME Type | video/x-vnd.on2.vp9 |
| Owner | default (vendor) |
| Rank | 256 |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| 0 | 5.2 |

**Measured Performance:**
| Resolution | Frame Rate |
|------------|------------|
| 320x180 | 600-1300 fps |
| 640x360 | 500-800 fps |
| 1280x720 | 400-600 fps |
| 1920x1080 | 350-420 fps |
| 3840x2160 | 100-130 fps |

---

### VC-1/WMV Decoder

| Property | `OMX.Intel.hw_vd.vc1` |
|----------|----------------------|
| MIME Type | video/x-ms-wmv |
| Owner | default (vendor) |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |

**Features:**
- Adaptive playback
- Performance point: 3840x2160 @ 60 fps
- Windows Media Video compatibility

---

## Hardware Video Encoders (Intel)

### H.264/AVC Encoder

| Property | `OMX.Intel.hw_ve.h264` |
|----------|------------------------|
| MIME Type | video/avc |
| Owner | default (vendor) |
| Rank | 256 |
| Hardware Accelerated | Yes |
| Resolution Range | 176x144 - 3840x2160 |
| Max Bitrate | 40 Mbps |
| Block Size | 16x16 |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Baseline | 5.1 |
| Main | 5.1 |
| High | 5.1 |

**Features:**
- Performance point: 3840x2160 @ 60 fps
- VBR and CBR modes

---

### H.265/HEVC Encoder

| Property | `OMX.Intel.hw_ve.h265` |
|----------|------------------------|
| MIME Type | video/hevc |
| Owner | default (vendor) |
| Rank | 256 |
| Hardware Accelerated | Yes |
| Resolution Range | 64x64 - 3840x2160 |
| Max Bitrate | 40 Mbps |

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Main | 5.1 |

---

## Software Video Decoders

### H.264/AVC Software Decoder

| Property | `c2.android.avc.decoder` |
|----------|--------------------------|
| MIME Type | video/avc |
| Owner | codec2::software |
| Rank | 512 (fallback) |
| Software Only | Yes |
| Resolution Range | 2x2 - 4080x4080 |
| Max Bitrate | 48 Mbps |
| Block Count Range | 1 - 32,768 |

**Aliases:** `OMX.google.h264.decoder`

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Constrained Baseline | 5.2 |
| Baseline | 5.2 |
| Main | 5.2 |
| Constrained High | 5.2 |
| High | 5.2 |

**Measured Performance:**
| Resolution | Frame Rate |
|------------|------------|
| 320x240 | 150-180 fps |
| 720x480 | 60-80 fps |
| 1280x720 | 25-30 fps |
| 1920x1080 | 11-13 fps |

---

### H.265/HEVC Software Decoder

| Property | `c2.android.hevc.decoder` |
|----------|---------------------------|
| MIME Type | video/hevc |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 4096x4096 |
| Max Bitrate | 10 Mbps |

**Aliases:** `OMX.google.hevc.decoder`

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Main | High 5.2 |
| MainStill | High 5.2 |

---

### VP8 Software Decoder

| Property | `c2.android.vp8.decoder` |
|----------|--------------------------|
| MIME Type | video/x-vnd.on2.vp8 |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 40 Mbps |

**Aliases:** `OMX.google.vp8.decoder`

---

### VP9 Software Decoder

| Property | `c2.android.vp9.decoder` |
|----------|--------------------------|
| MIME Type | video/x-vnd.on2.vp9 |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 40 Mbps |

**Aliases:** `OMX.google.vp9.decoder`

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| 0 | 5 |
| 2 | 5 |
| 2HDR | 5 |
| 2HDRPlus | 5 |

---

### AV1 Software Decoder

| Property | `c2.android.av1.decoder` |
|----------|--------------------------|
| MIME Type | video/av01 |
| Owner | codec2::software |
| Rank | 512 |
| Software Only | Yes |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 120 Mbps |

**Note:** No hardware AV1 decoder available.

**Supported Profiles:**
| Profile | Level |
|---------|-------|
| Main8 | 5.3 |
| Main10HDR10 | 5.3 |
| Main10HDRPlus | 5.3 |

---

### H.263 Software Decoder

| Property | `c2.android.h263.decoder` |
|----------|---------------------------|
| MIME Type | video/3gpp |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 352x288 |
| Max Bitrate | 384 Kbps |

**Aliases:** `OMX.google.h263.decoder`

---

### MPEG-4 Software Decoder

| Property | `c2.android.mpeg4.decoder` |
|----------|----------------------------|
| MIME Type | video/mp4v-es |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 384 Kbps |

**Aliases:** `OMX.google.mpeg4.decoder`

---

## Software Video Encoders

### H.264/AVC Software Encoder

| Property | `c2.android.avc.encoder` |
|----------|--------------------------|
| MIME Type | video/avc |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 16x16 - 2048x2048 |
| Max Bitrate | 12 Mbps |
| Features | Intra-refresh |

**Aliases:** `OMX.google.h264.encoder`

---

### H.265/HEVC Software Encoder

| Property | `c2.android.hevc.encoder` |
|----------|---------------------------|
| MIME Type | video/hevc |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 512x512 |
| Max Bitrate | 4 Mbps |

**Note:** Limited resolution compared to hardware encoder.

---

### VP8 Software Encoder

| Property | `c2.android.vp8.encoder` |
|----------|--------------------------|
| MIME Type | video/x-vnd.on2.vp8 |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 40 Mbps |
| Features | VBR, CBR |

---

### VP9 Software Encoder

| Property | `c2.android.vp9.encoder` |
|----------|--------------------------|
| MIME Type | video/x-vnd.on2.vp9 |
| Owner | codec2::software |
| Rank | 512 |
| Resolution Range | 2x2 - 2048x2048 |
| Max Bitrate | 40 Mbps |
| Features | VBR, CBR |

---

## Codec Selection Priority

### Decoder Priority (by Rank)

| Priority | Rank | Codec Type | Example |
|----------|------|------------|---------|
| 1 (Highest) | 256 | Intel Hardware | `OMX.Intel.hw_vd.h264` |
| 2 | 512 | Android Codec2 | `c2.android.avc.decoder` |
| 3 (Lowest) | 528 | Legacy OMX | `OMX.google.h264.decoder` |

### Encoder Priority (by Rank)

| Priority | Rank | Codec Type | Example |
|----------|------|------------|---------|
| 1 (Highest) | 256 | Intel Hardware | `OMX.Intel.hw_ve.h264` |
| 2 | 512 | Android Codec2 | `c2.android.avc.encoder` |
| 3 (Lowest) | 528 | Legacy OMX | `OMX.google.h264.encoder` |

---

## Video Encoder Capabilities (Media Profiles)

### Encoder Output Formats

| Format | Supported |
|--------|-----------|
| MP4 | Yes |
| 3GP | Yes |

### Video Encoder Limits

| Codec | Min Resolution | Max Resolution | Min Bitrate | Max Bitrate | Max FPS |
|-------|----------------|----------------|-------------|-------------|---------|
| H.264 | 176x144 | 3840x2160 | 64 Kbps | 15 Mbps | 30 |
| H.263 | 176x144 | 352x288 | 64 Kbps | 2 Mbps | 30 |
| MPEG-4 | 176x144 | 352x288 | 64 Kbps | 8 Mbps | 30 |

---

## Color Formats Summary

### Hardware Codecs

| Format Code | Format Name | Usage |
|-------------|-------------|-------|
| 0x7f420888 | YUV420Flexible | Recommended |
| 0x15 | YUV420SemiPlanar (NV12) | Native |
| 0x100 | Implementation-defined | Platform specific |

### Software Codecs

| Format Code | Format Name | Usage |
|-------------|-------------|-------|
| 0x7f420888 | YUV420Flexible | Recommended |
| 0x13 | YUV420Planar (I420) | Standard |
| 0x15 | YUV420SemiPlanar (NV12) | Alternate |
| 0x14 | YUV420PackedPlanar | Legacy |
| 0x27 | YUV420PackedSemiPlanar | Legacy |

---

## CarPlay/Android Auto Recommendations

### Optimal Decode Configuration

| Parameter | Recommended Value |
|-----------|-------------------|
| Codec | H.264 (video/avc) |
| Decoder | `OMX.Intel.hw_vd.h264` |
| Profile | Baseline or Main |
| Level | 4.1 or lower |
| Resolution | 1920x1080 or 1280x720 |
| Frame Rate | 30 or 60 fps |
| Bitrate | 5-15 Mbps |
| Color Format | YUV420SemiPlanar (NV12) |

### Optimal Encode Configuration (Dash Cam, etc.)

| Parameter | Recommended Value |
|-----------|-------------------|
| Codec | H.264 (video/avc) |
| Encoder | `OMX.Intel.hw_ve.h264` |
| Profile | High |
| Resolution | 1920x1080 |
| Frame Rate | 30 fps |
| Bitrate | 8-15 Mbps |
| Mode | CBR (constant bitrate) |
