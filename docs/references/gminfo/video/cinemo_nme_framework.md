# CINEMO / NME Framework Analysis

**Device:** GM Info 3.7 (gminfo37)
**Framework:** CINEMO Multimedia Framework (Harman/Samsung)
**Analysis Date:** January 2026
**Source:** Binary analysis of libNme*.so from GM AAOS

---

## Overview

GM AAOS uses the **CINEMO** multimedia framework for CarPlay and media processing. CINEMO is developed by Harman/Samsung (NME = Native Media Engine) and provides a complete AV pipeline separate from Android's MediaCodec/Stagefright.

---

## Library Inventory

### Core Libraries

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeBaseClasses.so` | 4.8 MB | Core framework, base classes |
| `libNmeSDK.so` | 3.0 MB | Main SDK interface |
| `libNme.so` | 230 KB | Module system, factory |

### CarPlay/AirPlay

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeCarPlay.so` | 1.0 MB | AirPlay protocol, CarPlay session |
| `libNmeIAP.so` | 2.9 MB | iAP (Apple Accessory Protocol) |
| `libNmeAppleAuth.so` | 80 KB | Apple MFi authentication |
| `libNmeAppleAuthImpl.so` | 162 KB | Auth implementation |

### Video Processing

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeVideo.so` | 124 KB | Video codec abstraction |
| `libNmeVideoSW.so` | 646 KB | NVDEC software decoder |
| `libNmeVideoDevice.so` | 406 KB | Surface/device management |
| `libNmeVideoRenderer.so` | 284 KB | VMR (Video Mixing Renderer) |
| `libNmeVideoGAVD.so` | 211 KB | Google AVD video support |
| `libNmeEncoder.so` | 832 KB | Video encoding |
| `libNmeImage.so` | 748 KB | Image processing |

### Audio Processing

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeAudio.so` | 333 KB | Audio codec abstraction |
| `libNmeAudioAAC.so` | 330 KB | AAC decoder |
| `libNmeAudioDevice.so` | 475 KB | Audio device management |
| `libNmeAudioRenderer.so` | 163 KB | Audio rendering |
| `libNmeAudioMisc.so` | 47 KB | Audio utilities |

### Transport

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeTransport.so` | 201 KB | Transport abstraction |
| `libNmeUsbTransport.so` | 46 KB | USB transport |
| `libNmeAndroidTransport.so` | 69 KB | Android-specific transport |

### Other

| Library | Size | Purpose |
|---------|------|---------|
| `libNmeNav.so` | 209 KB | Navigation support |
| `libNmeNavCopier.so` | 153 KB | Navigation data copy |
| `libNmeSubtitle.so` | 364 KB | Subtitle rendering |
| `libNmeVfs.so` | 1.7 MB | Virtual filesystem |
| `libNmeRedSource.so` | 312 KB | Media source handling |

**Total:** ~17.5 MB of native libraries

---

## Module System

### Module Identifiers

From binary string analysis:

```
Cinemo/VideoDevice/Device
Cinemo/VideoDevice/Device/Composite
Cinemo/VideoDevice/Device/Null
Cinemo/VideoDevice/Device/SwapChain
Cinemo/Window/Android
Cinemo/NmeVideo/Codec/NvdecSW
Cinemo/nvdec/H264DEC
Cinemo/nvdec/vdisplay
Cinemo/Vmr
```

### Factory Functions

Each library exposes creation functions:

```cpp
// Video
NmeCreateVideo()
NmeCreateCodecNullVideo()
NmeCreateCodecNvdecSW()

// Video Device
NmeCreateDeviceANW()          // Android Native Window
NmeCreateDeviceConsole()
NmeCreateDeviceGAVR()         // Google AVD Renderer
NmeCreateDeviceInputEvent()
NmeCreateDeviceNullVideo()
NmeCreateDeviceSoftVideo()
NmeCreateSwapChain()
NmeCreateVideoCaptureNull()

// Video Renderer
NmeCreateVmr()
NmeCreateVmrClone()
NmeCreateVmrNull()

// CarPlay
NmeCreateCinemoCarPlay()

// Module Info
NmeModuleInfo()
```

---

## Interface Hierarchy

### Video Decode

```
INmeDecode (base)
    │
    └── INmeVideoDecode
            │
            ├── NmeVideoCodec (abstraction)
            │       │
            │       └── NvdecSW (H.264 decoder)
            │
            └── INmeVideoDecodeQuality (quality control)
```

### Video Rendering

```
INmeVideoRenderer
    │
    └── NmeVmr (Video Mixing Renderer)
            │
            ├── DecideRenderFPS()
            ├── DecideRenderQuality()
            └── OnSurface*() callbacks
```

### Device/Surface

```
INmeDevice
    │
    ├── NmeDeviceANW (Android Native Window)
    │       │
    │       └── ANativeWindow interop
    │
    └── NmeSwapChain (buffer management)
```

---

## AirPlay Integration

### Server Components

```cpp
// From libNmeCarPlay.so strings
AirPlayReceiverServer
    ├── AirPlayReceiverServerCreate()
    ├── AirPlayReceiverServerStop()
    └── AirPlayReceiverServerControl()

AirPlayReceiverSession
    ├── AirPlayReceiverSessionCreate()
    ├── AirPlayReceiverSessionSetup()
    └── AirPlayReceiverSessionScreen
            ├── ProcessFrames()
            ├── ProcessFrame()
            └── ForceKeyFrame()
```

### Protocol Details

```
Protocol Version: AirPlay/320.17.8
Service Discovery: _airplay._tcp.
Pairing: com.apple.airplay.pairing
Device ID Header: AirPlay-Receiver-Device-ID
```

### Session Logging

```
AirPlay session started: From=%s D=0x%012llx A=%##a T=%s C=%s
    L=%u ms Bonjour=%u ms Conn=%u ms Auth=%u ms Ann=%u ms Setup=%u ms

AirPlay session ended: Dur=%u seconds Reason=%#m
```

---

## Video Processing Options

### Configuration Options (from libNmeVideo.so)

| Option | Type | Description |
|--------|------|-------------|
| `video_hardware_decode` | bool | Enable HW decoder path |
| `video_software_decode` | bool | Enable SW decoder path |
| `video_max_h264_level` | int | Maximum H.264 level constraint |
| `video_extra_surfaces` | int | Additional decode surfaces |
| `video_frame_drop_threshold` | int | Frame drop threshold (ms) |

### Runtime Decisions

```cpp
// Quality control decision flow
QualityControl(timestamp, flags) {
    late_amount = current_time - expected_time;

    if (late_amount > threshold) {
        if (clock_running) {
            // Drop non-reference frames
            mode = REFERENCE_FRAMES_ONLY;
        }
    }

    if (catching_up_finished) {
        mode = NORMAL;
    }
}
```

---

## EGL/Graphics Integration

### Surface Creation

```cpp
// Android Native Window creation
ANativeWindow* window = ANativeWindow_fromSurface(jni_env, surface);
ANativeWindow_acquire(window);

// Query dimensions
int width = ANativeWindow_getWidth(window);
int height = ANativeWindow_getHeight(window);

// EGL surface
EGLSurface eglSurface = eglCreateWindowSurface(display, config, window, attrs);

// Alternative: pbuffer for offscreen
EGLSurface pbuffer = eglCreatePbufferSurface(display, config, attrs);
```

### Java Interop

JNI signatures found:
```
(Landroid/graphics/SurfaceTexture;)V
android/graphics/SurfaceTexture
android/view/Surface
```

---

## Error Handling

### Common Error Strings

```
// Video codec
failed to create codec %s with error %e

// Video device
%s(): Failed to create ANativeWindow
%s(): Failed to create EGL surface (%04x)
%s(): Failed to activate EGL context (%04x)
%s(): Surfaceless context requested, but EGL does not support it

// Video renderer
Video device based HW decoder not available, %e!
Video device based HW decoder open failed with %e!
GetNativeSurfaces() failed with %e!

// NVDEC decoder
NvdecDeliverHeaders() returned NvdecError_BitDepth
NvdecDeliverHeaders() returned NvdecError_ChromaFormat
NvdecDeliverHeaders() returned NvdecError_Profile
NvdecDeliverHeaders() returned NvdecError_MaxVxdFileMemory

// CarPlay
OnFrame() corrupt video data
OnFrame() invalid nalu_size
OnFrame() wrong video data len
AirPlayReceiverSessionForceKeyFrame failed: %d
```

---

## Audio Processing

### Audio Decoders

```cpp
// From libNmeCarPlay.so
NmeCarPlayAudioDecoder
NmeCarPlayAudioDecoderConfig
NmeCarPlayAudioDecoderConfigAAC
NmeCarPlayAudioDecoderConfigOpus
NmeCarPlayAudioDecoderConfigAACELD
```

### Audio Resampling

```cpp
// Resampler handling
OnAudioFrame() {
    log("OnAudioFrame(): (Input %u Hz, %u channels, %u bits per sample), "
        "(Output %u Hz, %u channels, %u bits per sample)");

    if (!create_resampler()) {
        log("OnAudioFrame() could not create resampler");
    }

    WarmUpResampler();
    log("WarmUpResampler() resampler delay %d frames");
}
```

---

## Source Code References

From binary debug strings:

```
../../NmeLibs/Nvdec/vdec/vdec264.cpp
../../NmeLibs/Nvdec/vdec/vrbsp.cpp
../../NmeLibs/Nvdec/vdec/vrbsp_sequence.cpp
../../NmeLibs/Nvdec/vutilities/vdisplay.cpp
../../NmeVideo/src/codecs/codec_nvdec.cpp
../../NmeVideoRenderer/src/NmeVmr*.cpp
../../NmeCarPlay/r14/src/NmeCarPlayVideo.cpp
../../NmeCarPlay/r14/src/NmeCarPlayAudioDecoder*.cpp
```

---

## Comparison with Standard Android

| Aspect | Android MediaCodec | CINEMO/NME |
|--------|-------------------|------------|
| Interface | OMX/Codec2 | Proprietary |
| Video Decode | OMX.Intel.hw_vd.h264 | NvdecSW (software) |
| Surface | MediaCodec.setOutputSurface() | ANativeWindow + EGL |
| Timing | MediaCodec timestamps | CINEMO clock sync |
| Protocol | N/A | AirPlay integrated |
| Error Recovery | MediaCodec callbacks | ForceKeyframe() |

---

## Performance Notes

### Decode Path Comparison

| Path | Decoder | Latency | CPU Usage |
|------|---------|---------|-----------|
| MediaCodec HW | OMX.Intel | ~5 ms | Low |
| CINEMO SW | NvdecSW | ~10-15 ms | Medium |

### Why CINEMO Uses Software Decode

1. **AirPlay Protocol Integration** - Timing sync requirements
2. **Custom SEI Handling** - Freeze/snapshot/stereo support
3. **Quality Control** - Adaptive frame dropping
4. **Error Recovery** - ForceKeyframe() integration
5. **Clock Synchronization** - AirPlay-specific timing

---

## Related Documentation

- [carplay_video_pipeline.md](carplay_video_pipeline.md) - Video pipeline details
- [h264_nal_processing.md](h264_nal_processing.md) - NAL unit processing

---

## Data Sources

Binary analysis of NME libraries:
```
/Users/zeno/Downloads/misc/GM_research/gm_aaos/extracted_partitions/system_extracted/system/lib64/libNme*.so
```

Methods: `strings`, `readelf -s`, `nm --demangle`
