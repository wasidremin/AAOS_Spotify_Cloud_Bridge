# Intel Media SDK on GM AAOS

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**SDK Version:** Intel Media SDK 1.x (legacy)
**Analysis Date:** January 2026

---

## Overview

GM AAOS uses the **Intel Media SDK (MFX)** for hardware-accelerated video decode, encode, and processing. The SDK interfaces with the Intel VPU through VA-API and the i965 driver.

**Third-Party Access:** YES - Intel hardware codecs are accessible to third-party apps via standard Android MediaCodec API. See [../third_party_access.md](../third_party_access.md) for code examples.

---

## Offline Documentation

The following official Intel documentation has been downloaded for offline reference:

| Document | File | Size | Description |
|----------|------|------|-------------|
| API Reference | [mediasdk-man.pdf](mediasdk-man.pdf) | 1.4 MB | Complete API reference (Version 1.27) |
| Developer's Guide | [intel-media-developers-guide.pdf](intel-media-developers-guide.pdf) | 4.4 MB | Tutorials, design fundamentals |

### Online Resources

| Resource | URL |
|----------|-----|
| Intel Developer Reference | https://www.intel.com/content/www/us/en/content-details/671472/intel-media-sdk-developer-reference.html |
| GitHub Repository | https://github.com/Intel-Media-SDK/MediaSDK |
| GitHub Documentation | https://github.com/Intel-Media-SDK/MediaSDK/blob/master/doc/mediasdk-man.md |
| Community Forum | https://community.intel.com/t5/Media-Intel-Video-Processing/bd-p/media-products |

---

## GM AAOS Library Inventory

### Core Libraries

| Library | Path | Size | Purpose |
|---------|------|------|---------|
| `libmfxhw64.so` | `/vendor/lib64/` | 9.3 MB | Intel Media SDK core |
| `libmfxhw32.so` | `/vendor/lib/` | - | 32-bit variant |
| `libmfx_omx_core.so` | `/vendor/lib64/` | 12 KB | OMX IL core adapter |
| `libmfx_omx_components_hw.so` | `/vendor/lib64/` | 350 KB | OMX hardware components |

### VA-API Libraries

| Library | Path | Size | Purpose |
|---------|------|------|---------|
| `libva.so` | `/vendor/lib64/` | 155 KB | VA-API core |
| `libva-android.so` | `/vendor/lib64/` | 8 KB | Android VA binding |
| `i965_drv_video.so` | `/vendor/lib64/` | 30 MB | Intel i965 VA driver |

### Configuration

| File | Path | Purpose |
|------|------|---------|
| `mfx_omxil_core.conf` | `/vendor/etc/` | OMX component registration |
| `media_codecs.xml` | `/vendor/etc/` | Android MediaCodec definitions |

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│                        APPLICATION LAYER                            │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────┐         ┌─────────────────────┐           │
│  │   Android Auto      │         │     CarPlay         │           │
│  │   (MediaCodec)      │         │   (CINEMO/NME)      │           │
│  └──────────┬──────────┘         └──────────┬──────────┘           │
│             │                               │                       │
│             │ Uses Intel MFX                │ Does NOT use MFX      │
│             ▼                               ▼                       │
├─────────────────────────────────────────────────────────────────────┤
│                        FRAMEWORK LAYER                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    Android MediaCodec                        │   │
│  │                    (Stagefright/Codec2)                      │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
│                             ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                    OMX IL (OpenMAX)                          │   │
│  │                    libmfx_omx_core.so                        │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
│                             ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              OMX Hardware Components                         │   │
│  │              libmfx_omx_components_hw.so                     │   │
│  │                                                              │   │
│  │  Decoders:                      Encoders:                    │   │
│  │  ├─ OMX.Intel.hw_vd.h264       ├─ OMX.Intel.hw_ve.h264      │   │
│  │  ├─ OMX.Intel.hw_vd.h264.secure├─ OMX.Intel.hw_ve.h265      │   │
│  │  ├─ OMX.Intel.hw_vd.h265                                     │   │
│  │  ├─ OMX.Intel.hw_vd.h265.secure                              │   │
│  │  ├─ OMX.Intel.hw_vd.vp8                                      │   │
│  │  ├─ OMX.Intel.hw_vd.vp9                                      │   │
│  │  ├─ OMX.Intel.hw_vd.vc1                                      │   │
│  │  └─ OMX.Intel.hw_vd.mp2                                      │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
├─────────────────────────────────────────────────────────────────────┤
│                        INTEL MEDIA SDK                              │
├─────────────────────────────────────────────────────────────────────┤
│                             │                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              Intel Media SDK (MFX)                           │   │
│  │              libmfxhw64.so (9.3 MB)                          │   │
│  │                                                              │   │
│  │  Session:          Decode:           Encode:          VPP:   │   │
│  │  MFXInit           MFXVideoDECODE_*  MFXVideoENCODE_* MFXVideoVPP_*│
│  │  MFXClose          DecodeFrameAsync  EncodeFrameAsync RunFrameVPPAsync│
│  │  MFXQueryVersion   DecodeHeader      Query            Query  │   │
│  │  MFXQueryIMPL      QueryIOSurf       QueryIOSurf      QueryIOSurf│
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
├─────────────────────────────────────────────────────────────────────┤
│                        VA-API LAYER                                 │
├─────────────────────────────────────────────────────────────────────┤
│                             │                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              VA-API (Video Acceleration API)                 │   │
│  │              libva.so + libva-android.so                     │   │
│  │                                                              │   │
│  │  Surface:          Context:          Picture:                │   │
│  │  vaCreateSurfaces  vaCreateContext   vaBeginPicture          │   │
│  │  vaDestroySurfaces vaDestroyContext  vaEndPicture            │   │
│  │  vaSyncSurface     vaCreateConfig    vaRenderPicture         │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
├─────────────────────────────────────────────────────────────────────┤
│                        DRIVER LAYER                                 │
├─────────────────────────────────────────────────────────────────────┤
│                             │                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              i965 VA Driver (Broxton/Apollo Lake)            │   │
│  │              i965_drv_video.so (30 MB)                       │   │
│  │                                                              │   │
│  │  CodechalDecodeNv12ToP010G9Bxt                               │   │
│  │  CodechalEncodeAvcEncG9Bxt                                   │   │
│  │  CodechalEncHevcStateG10                                     │   │
│  └──────────────────────────┬──────────────────────────────────┘   │
│                             │                                       │
├─────────────────────────────────────────────────────────────────────┤
│                        HARDWARE                                     │
├─────────────────────────────────────────────────────────────────────┤
│                             │                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │              Intel VPU (Apollo Lake GPU)                     │   │
│  │              Intel HD Graphics 505                           │   │
│  │                                                              │   │
│  │  Fixed-function decode/encode units                          │   │
│  │  4K60 H.264/H.265/VP8/VP9 decode                            │   │
│  │  4K60 H.264/H.265 encode                                     │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

---

## OMX Component Registration

From `/vendor/etc/mfx_omxil_core.conf`:

```
OMX.Intel.hw_vd.vc1          : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.h264         : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.h264.secure  : libmfx_omx_components_hw.so
OMX.Intel.hw_ve.h264         : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.vp8          : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.vp9          : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.mp2          : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.h265         : libmfx_omx_components_hw.so
OMX.Intel.hw_vd.h265.secure  : libmfx_omx_components_hw.so
OMX.Intel.hw_ve.h265         : libmfx_omx_components_hw.so
```

---

## Codec Capabilities

### Hardware Decoders

| Codec | OMX Component | Max Resolution | Max FPS | Bitrate | Profiles |
|-------|---------------|----------------|---------|---------|----------|
| H.264/AVC | OMX.Intel.hw_vd.h264 | 3840x2160 | 60 | 40 Mbps | Baseline, Main, High (5.1) |
| H.264 Secure | OMX.Intel.hw_vd.h264.secure | 3840x2160 | 60 | 40 Mbps | DRM playback |
| H.265/HEVC | OMX.Intel.hw_vd.h265 | 3840x2160 | 60 | 40 Mbps | Main, Main10 (5.1) |
| H.265 Secure | OMX.Intel.hw_vd.h265.secure | 3840x2160 | 60 | 40 Mbps | DRM playback |
| VP8 | OMX.Intel.hw_vd.vp8 | 3840x2160 | 60 | 40 Mbps | - |
| VP9 | OMX.Intel.hw_vd.vp9 | 3840x2160 | 60 | 40 Mbps | Profile 0-2, HDR (5.2) |
| VC-1 | OMX.Intel.hw_vd.vc1 | 3840x2160 | 60 | 40 Mbps | - |
| MPEG-2 | OMX.Intel.hw_vd.mp2 | - | - | - | - |

### Hardware Encoders

| Codec | OMX Component | Max Resolution | Max FPS | Bitrate |
|-------|---------------|----------------|---------|---------|
| H.264/AVC | OMX.Intel.hw_ve.h264 | 3840x2160 | 60 | 40 Mbps |
| H.265/HEVC | OMX.Intel.hw_ve.h265 | 3840x2160 | 60 | 40 Mbps |

---

## Intel Media SDK API Reference

### Session Management

```c
// Initialize SDK session
mfxStatus MFXInit(mfxIMPL impl, mfxVersion *ver, mfxSession *session);
mfxStatus MFXInitEx(mfxInitParam par, mfxSession *session);

// Close session
mfxStatus MFXClose(mfxSession session);

// Query implementation
mfxStatus MFXQueryIMPL(mfxSession session, mfxIMPL *impl);
mfxStatus MFXQueryVersion(mfxSession session, mfxVersion *version);
```

### Video Decode

```c
// Initialize decoder
mfxStatus MFXVideoDECODE_Init(mfxSession session, mfxVideoParam *par);

// Decode frame (async)
mfxStatus MFXVideoDECODE_DecodeFrameAsync(
    mfxSession session,
    mfxBitstream *bs,
    mfxFrameSurface1 *surface_work,
    mfxFrameSurface1 **surface_out,
    mfxSyncPoint *syncp
);

// Parse header without decode
mfxStatus MFXVideoDECODE_DecodeHeader(
    mfxSession session,
    mfxBitstream *bs,
    mfxVideoParam *par
);

// Query capabilities
mfxStatus MFXVideoDECODE_Query(
    mfxSession session,
    mfxVideoParam *in,
    mfxVideoParam *out
);

// Query surface requirements
mfxStatus MFXVideoDECODE_QueryIOSurf(
    mfxSession session,
    mfxVideoParam *par,
    mfxFrameAllocRequest *request
);

// Reset decoder
mfxStatus MFXVideoDECODE_Reset(mfxSession session, mfxVideoParam *par);

// Close decoder
mfxStatus MFXVideoDECODE_Close(mfxSession session);
```

### Video Encode

```c
// Initialize encoder
mfxStatus MFXVideoENCODE_Init(mfxSession session, mfxVideoParam *par);

// Encode frame (async)
mfxStatus MFXVideoENCODE_EncodeFrameAsync(
    mfxSession session,
    mfxEncodeCtrl *ctrl,
    mfxFrameSurface1 *surface,
    mfxBitstream *bs,
    mfxSyncPoint *syncp
);

// Query capabilities
mfxStatus MFXVideoENCODE_Query(
    mfxSession session,
    mfxVideoParam *in,
    mfxVideoParam *out
);

// Query surface requirements
mfxStatus MFXVideoENCODE_QueryIOSurf(
    mfxSession session,
    mfxVideoParam *par,
    mfxFrameAllocRequest *request
);

// Reset encoder
mfxStatus MFXVideoENCODE_Reset(mfxSession session, mfxVideoParam *par);

// Close encoder
mfxStatus MFXVideoENCODE_Close(mfxSession session);
```

### Video Processing (VPP)

```c
// Initialize VPP
mfxStatus MFXVideoVPP_Init(mfxSession session, mfxVideoParam *par);

// Process frame (async)
mfxStatus MFXVideoVPP_RunFrameVPPAsync(
    mfxSession session,
    mfxFrameSurface1 *in,
    mfxFrameSurface1 *out,
    mfxExtVppAuxData *aux,
    mfxSyncPoint *syncp
);

// Query capabilities
mfxStatus MFXVideoVPP_Query(
    mfxSession session,
    mfxVideoParam *in,
    mfxVideoParam *out
);

// Close VPP
mfxStatus MFXVideoVPP_Close(mfxSession session);
```

### Synchronization

```c
// Wait for async operation to complete
mfxStatus MFXVideoCORE_SyncOperation(
    mfxSession session,
    mfxSyncPoint syncp,
    mfxU32 wait    // milliseconds, MFX_INFINITE for blocking
);
```

---

## Key Data Structures

### mfxVideoParam

```c
typedef struct {
    mfxU32 reserved[3];
    mfxU16 AsyncDepth;           // Pipeline depth for async operations

    union {
        mfxInfoMFX mfx;          // Codec parameters
        mfxInfoVPP vpp;          // VPP parameters
    };

    mfxU16 Protected;            // DRM protection mode
    mfxU16 IOPattern;            // Input/output memory type
    mfxExtBuffer **ExtParam;     // Extended parameters
    mfxU16 NumExtParam;
} mfxVideoParam;
```

### mfxFrameSurface1

```c
typedef struct {
    mfxFrameInfo Info;           // Frame properties
    mfxFrameData Data;           // Frame data pointers
    mfxU16 reserved[2];
} mfxFrameSurface1;
```

### mfxBitstream

```c
typedef struct {
    mfxU8 *Data;                 // Bitstream data pointer
    mfxU32 DataOffset;           // Current read position
    mfxU32 DataLength;           // Valid data length
    mfxU32 MaxLength;            // Buffer capacity
    mfxU64 TimeStamp;            // Presentation timestamp
    mfxU64 DecodeTimeStamp;      // Decode timestamp
    mfxU16 PicStruct;            // Picture structure
    mfxU16 FrameType;            // Frame type (I/P/B)
    mfxU16 DataFlag;             // Data flags
    // ...
} mfxBitstream;
```

---

## Status Codes

| Code | Value | Description |
|------|-------|-------------|
| MFX_ERR_NONE | 0 | Success |
| MFX_ERR_UNKNOWN | -1 | Unknown error |
| MFX_ERR_NULL_PTR | -2 | Null pointer |
| MFX_ERR_UNSUPPORTED | -3 | Feature unsupported |
| MFX_ERR_MEMORY_ALLOC | -4 | Memory allocation failed |
| MFX_ERR_NOT_ENOUGH_BUFFER | -5 | Buffer too small |
| MFX_ERR_INVALID_HANDLE | -6 | Invalid session handle |
| MFX_ERR_LOCK_MEMORY | -7 | Memory lock failed |
| MFX_ERR_NOT_INITIALIZED | -8 | Not initialized |
| MFX_ERR_NOT_FOUND | -9 | Resource not found |
| MFX_ERR_MORE_DATA | -10 | Need more input data |
| MFX_ERR_MORE_SURFACE | -11 | Need more output surfaces |
| MFX_ERR_ABORTED | -12 | Operation aborted |
| MFX_ERR_DEVICE_LOST | -13 | Hardware device lost |
| MFX_ERR_INCOMPATIBLE_VIDEO_PARAM | -14 | Incompatible parameters |
| MFX_ERR_INVALID_VIDEO_PARAM | -15 | Invalid parameters |
| MFX_ERR_UNDEFINED_BEHAVIOR | -16 | Undefined behavior |
| MFX_ERR_DEVICE_FAILED | -17 | Device operation failed |
| MFX_WRN_IN_EXECUTION | 1 | Operation in progress |
| MFX_WRN_DEVICE_BUSY | 2 | Device busy |
| MFX_WRN_VIDEO_PARAM_CHANGED | 3 | Parameters changed |
| MFX_WRN_PARTIAL_ACCELERATION | 4 | Partial HW acceleration |
| MFX_WRN_INCOMPATIBLE_VIDEO_PARAM | 5 | Incompatible but usable |
| MFX_WRN_VALUE_NOT_CHANGED | 6 | Value unchanged |
| MFX_WRN_OUT_OF_RANGE | 7 | Value clamped |

---

## Usage in Projection

### Android Auto

Android Auto uses the Intel Media SDK through the standard Android MediaCodec API:

```
App → MediaCodec → OMX.Intel.hw_vd.h264 → MFX → VA-API → i965 → VPU
```

**Benefits:**
- Hardware-accelerated 4K60 decode
- Low latency (~5ms decode)
- Low CPU usage

### CarPlay

CarPlay does **NOT** use Intel Media SDK:

```
AirPlay → libNmeCarPlay.so → libNmeVideoSW.so (NVDEC software) → EGL
```

**Reasons:**
- AirPlay timing synchronization requirements
- Custom SEI message handling
- ForceKeyframe() integration
- CINEMO is Harman's proprietary stack

---

## VPP Capabilities

Video Processing Pipeline filters available:

| Filter | Description |
|--------|-------------|
| Color Conversion | NV12, YV12, RGB32, P010, etc. |
| Deinterlacing | Bob, Advanced, Motion Adaptive |
| Denoise | Spatial and temporal noise reduction |
| Detail Enhancement | Sharpening |
| Frame Rate Conversion | Interpolation |
| Image Stabilization | Motion compensation |
| Composition | Multi-input blending |
| Resize | Scaling with various algorithms |

---

## oneVPL Note

Intel Media SDK has been **superseded** by **Intel oneAPI Video Processing Library (oneVPL)** for newer platforms (Tiger Lake and later). However, Apollo Lake (Broxton) in GM AAOS continues to use the legacy Media SDK.

| Platform | API |
|----------|-----|
| Apollo Lake (GM AAOS) | Intel Media SDK |
| Tiger Lake+ | oneVPL |

For oneVPL documentation: https://github.com/intel/libvpl

---

## Data Sources

**Extracted from GM AAOS:**
- `/vendor/lib64/libmfx*.so` - Binary analysis
- `/vendor/lib64/libva*.so` - Binary analysis
- `/vendor/lib64/i965_drv_video.so` - Binary analysis
- `/vendor/etc/mfx_omxil_core.conf` - Configuration
- `/vendor/etc/media_codecs.xml` - Codec definitions

**Official Intel Documentation:**
- Intel Media SDK Developer Reference
- Intel Media Developer's Guide

**Source:** `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`
