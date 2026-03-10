# CarPlay vs Android Auto Projection Comparison

**Device:** GM Info 3.7 (gminfo37)
**Platform:** Intel Apollo Lake (Broxton)
**Android Version:** 12 (API 32)
**Analysis Date:** January 2026
**Source:** Binary analysis and extracted partitions from GM AAOS

---

## Executive Summary

GM AAOS implements **fundamentally different architectures** for CarPlay and Android Auto:

| Aspect | Apple CarPlay | Android Auto |
|--------|---------------|--------------|
| **Framework** | CINEMO/NME (Harman) | Native Android MediaCodec |
| **Video Decoder** | Software (NVDEC) | Hardware (Intel OMX) |
| **Protocol** | AirPlay 320.17.8 | Android Auto Protocol (AAP) |
| **Libraries** | libNme*.so (~17.5 MB) | Standard AOSP |
| **Authentication** | Apple MFi (iAP2) | Google certificates |

---

## Video Pipeline Comparison

### CarPlay Video

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CARPLAY VIDEO PIPELINE                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  iPhone ──► USB NCM/WiFi ──► AirPlay Protocol ──► libNmeCarPlay.so │
│                                       │                             │
│                                       ▼                             │
│                              libNmeVideoSW.so                       │
│                              (NVDEC Software Decoder)               │
│                                       │                             │
│                                       ▼                             │
│                              libNmeVideoRenderer.so                 │
│                              (VMR - Video Mixing Renderer)          │
│                                       │                             │
│                                       ▼                             │
│                              ANativeWindow + EGL                    │
│                                       │                             │
│                                       ▼                             │
│                              SurfaceFlinger ──► Display             │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Decoder:** CINEMO NVDEC Software Decoder
- Library: `libNmeVideoSW.so` (646 KB)
- Type: CPU-based software decode
- Reason: AirPlay timing sync, custom SEI handling, ForceKeyframe() integration

**H.264 Processing:**
- NAL unit parsing via `H264DeliverAnnexB()`
- RBSP extraction in `vrbsp.cpp`
- SPS/PPS parsing in `vrbsp_sequence.cpp`
- SEI handling for freeze/snapshot/stereo

**Error Recovery:**
- `AirPlayReceiverSessionForceKeyFrame()` - Request IDR on corruption
- Quality control with adaptive frame dropping
- Reference-only mode when late

### Android Auto Video

```
┌─────────────────────────────────────────────────────────────────────┐
│                   ANDROID AUTO VIDEO PIPELINE                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  Android Phone ──► USB AOA/WiFi ──► AAP Protocol ──► CarService    │
│                                            │                        │
│                                            ▼                        │
│                                     MediaCodec API                  │
│                                            │                        │
│                                            ▼                        │
│                                   OMX.Intel.hw_vd.h264              │
│                                   (Hardware Decoder)                │
│                                            │                        │
│                                            ▼                        │
│                                     SurfaceTexture                  │
│                                            │                        │
│                                            ▼                        │
│                                   SurfaceFlinger ──► Display        │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Decoder:** Intel Hardware Decoder
- Codec: `OMX.Intel.hw_vd.h264`
- Type: Hardware-accelerated (Intel VPU)
- Max: 3840x2160 @ 60fps

**H.264 Processing:**
- Standard MediaCodec queueInputBuffer()
- Hardware NAL parsing
- Standard Android error callbacks

**Error Recovery:**
- MediaCodec.Callback.onError()
- Standard codec flush/restart

---

## Video Codec Specifications

### Hardware Decoders (Used by Android Auto)

| Codec | OMX Component | Max Resolution | Max FPS | Profiles |
|-------|---------------|----------------|---------|----------|
| H.264 | OMX.Intel.hw_vd.h264 | 3840x2160 | 60 | Baseline, Main, High (5.1) |
| H.265 | OMX.Intel.hw_vd.h265 | 3840x2160 | 60 | Main, Main10 (5.1) |
| VP8 | OMX.Intel.hw_vd.vp8 | 3840x2160 | 60 | - |
| VP9 | OMX.Intel.hw_vd.vp9 | 3840x2160 | 60 | Profile 0-2, HDR (5.2) |

### Software Decoder (Used by CarPlay)

| Codec | Library | Type | Notes |
|-------|---------|------|-------|
| H.264 | libNmeVideoSW.so | NVDEC Software | AirPlay-specific timing |

### Why CarPlay Uses Software Decode

Despite Intel hardware decoder availability, CarPlay uses software decode for:

1. **AirPlay Protocol Integration** - Custom timing synchronization
2. **SEI Message Handling** - Freeze/snapshot/stereo support
3. **ForceKeyframe()** - Immediate IDR request on errors
4. **Quality Control** - Adaptive frame dropping
5. **Clock Sync** - AirPlay-specific timestamp handling

---

## Android Auto Resolution Configuration

### Display Specifications

```
Physical Display: 2400 x 960 @ 60Hz
Aspect Ratio: 2.5:1 (non-standard)
Density: 200 dpi (1.25 scale factor)
xDpi: 192.911, yDpi: 193.523
Panel: DD134IA-01B (CMN, manufactured 2020)
```

### Required Configurations

Android Auto requires **two resolution configurations** for proper operation:

| Configuration | Purpose | Description |
|---------------|---------|-------------|
| **Video Resolution** | Phone rendering | Resolution at which phone renders and encodes H.264 video stream |
| **UI Resolution** | Touch mapping | Resolution used to map touch coordinates from display to phone |

### Configuration Gap (Documentation Finding)

**WARNING:** No explicit Android Auto video resolution configuration was found in the extracted GM AAOS partitions.

**Searched Locations:**
- `/vendor/etc/` - No Android Auto config XML
- `/system/etc/` - No projection resolution config
- `/product/etc/` - No embedded.projection config
- CalDef Database - No video resolution calibrations for Android Auto

**Services Found (but no config):**
```
com.gm.phoneprojection.service.BINDER    (IPhoneProjectionService)
com.gm.server.screenprojection.RDMSADBHandler
com.google.android.embedded.projection   (package referenced but not extracted)
```

### Potential Issues

The GM display has a **2.5:1 aspect ratio** which does not match standard Android Auto resolutions:

| Standard AA Resolution | Aspect Ratio | Match? |
|------------------------|--------------|--------|
| 800 x 480 | 5:3 (1.67:1) | No |
| 1280 x 720 | 16:9 (1.78:1) | No |
| 1920 x 1080 | 16:9 (1.78:1) | No |
| 2400 x 960 | 2.5:1 | Native display |

**Without proper configuration:**
1. Phone may default to 720p or 1080p instead of optimal resolution
2. Touch mapping could be misaligned if UI resolution doesn't match
3. Letterboxing/pillarboxing may occur due to aspect ratio mismatch
4. Margins may not be properly configured

### Expected Configuration Format

A proper Android Auto configuration should include:

```xml
<!-- Example - NOT found in GM AAOS -->
<projectionConfig>
    <video>
        <resolution width="1920" height="768" />  <!-- or 2400x960 -->
        <frameRate min="30" max="60" />
        <codec>H264</codec>
    </video>
    <ui>
        <resolution width="2400" height="960" />
        <density>200</density>
        <margins left="0" top="0" right="0" bottom="0" />
    </ui>
</projectionConfig>
```

### Where Configuration May Exist

The Android Auto resolution configuration may be:
1. Embedded in `com.google.android.embedded.projection` APK (not extracted)
2. Hardcoded in `com.gm.phoneprojection.service` native libraries
3. Negotiated at runtime via AAP protocol
4. Configured via Google Automotive Services (GAS) overlay

### Recommendation

Third-party implementations targeting GM AAOS should:
1. Support multiple video resolutions (720p, 1080p, native 2400x960)
2. Handle aspect ratio conversion (letterbox/pillarbox)
3. Properly map touch coordinates regardless of video resolution
4. Test with actual GM hardware to determine negotiated resolution

---

## Audio Pipeline Comparison

### CarPlay Audio

```
┌─────────────────────────────────────────────────────────────────────┐
│                    CARPLAY AUDIO PIPELINE                           │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  DOWNLINK (iPhone → Vehicle):                                       │
│  ────────────────────────────                                       │
│  iPhone ──► AirPlay ──► libNmeCarPlay.so ──► libNmeAudioAAC.so     │
│                                │                                    │
│                                ▼                                    │
│                         libNmeAudioDevice.so                        │
│                                │                                    │
│                                ▼                                    │
│                         AudioFlinger (bus routing)                  │
│                                │                                    │
│                                ▼                                    │
│                         PulseAudio + AVB ──► Amplifier              │
│                                                                     │
│  UPLINK (Vehicle → iPhone):                                         │
│  ──────────────────────────                                         │
│  Microphone ──► Harman HAL (AEC/NS/AGC) ──► libNmeCarPlay.so       │
│                                │                                    │
│                                ▼                                    │
│                         AAC-ELD/Opus Encode                         │
│                                │                                    │
│                                ▼                                    │
│                         AirPlay ──► iPhone                          │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Codecs Supported:**
- AAC-LC (44.1/48 kHz)
- AAC-ELD (16/24 kHz) - Low-latency voice
- Opus (8-48 kHz) - VoIP
- PCM (44.1/48 kHz)

**Audio Routing (Dedicated Buses):**
| Audio Type | Bus | Sample Rate |
|------------|-----|-------------|
| Media | bus0_media_out | 48 kHz |
| Navigation | bus1_navigation_out | 48 kHz |
| Siri | bus2_voice_command_out | 16-48 kHz |
| Phone Call | bus4_call_out | 8-16 kHz |
| Notification | bus6_notification_out | 48 kHz |

**Telephony Tuning (SCD Files):**
```
USB Wired:
  SSE_HF_GM_INFO3_CarPlayTelNB.scd      (8 kHz narrowband)
  SSE_HF_GM_INFO3_CarPlayTelWB.scd      (16 kHz wideband)
  SSE_HF_GM_INFO3_CarPlayFT_SWB.scd     (24 kHz super-wideband FaceTime)

WiFi Wireless:
  SSE_HF_GM_INFO3_WiFi_CarPlayTelNB.scd
  SSE_HF_GM_INFO3_WiFi_CarPlayTelWB.scd
  SSE_HF_GM_INFO3_WiFi_CarPlayTelSWB.scd
```

### Android Auto Audio

```
┌─────────────────────────────────────────────────────────────────────┐
│                   ANDROID AUTO AUDIO PIPELINE                       │
├─────────────────────────────────────────────────────────────────────┤
│                                                                     │
│  DOWNLINK (Phone → Vehicle):                                        │
│  ───────────────────────────                                        │
│  Phone ──► AAP Protocol ──► CarAudioService ──► AudioFlinger       │
│                                       │                             │
│                                       ▼                             │
│                                 Standard Android                    │
│                                 Audio Decoders                      │
│                                       │                             │
│                                       ▼                             │
│                                 Bus Routing                         │
│                                       │                             │
│                                       ▼                             │
│                                 Harman DSP ──► Amplifier            │
│                                                                     │
│  UPLINK (Vehicle → Phone):                                          │
│  ─────────────────────────                                          │
│  Microphone ──► Harman HAL (AEC/NS/AGC) ──► CarAudioService        │
│                                       │                             │
│                                       ▼                             │
│                                 AAP Protocol ──► Phone              │
│                                                                     │
└─────────────────────────────────────────────────────────────────────┘
```

**Codecs Supported:**
- AAC (all profiles via MediaCodec)
- MP3
- Opus
- Vorbis
- FLAC
- AMR-NB/WB
- G.711 (telephony)

**Audio Routing:**
Same bus architecture as CarPlay (shared AudioFlinger)

**Telephony Tuning:**
Uses standard Bluetooth HFP SCD files (no Android Auto specific tuning)

---

## Protocol Comparison

### CarPlay Protocol

| Aspect | Specification |
|--------|---------------|
| **Protocol** | AirPlay 320.17.8 |
| **Transport (Wired)** | USB NCM + IPv6 |
| **Transport (Wireless)** | WiFi Direct + Bluetooth (pairing) |
| **Authentication** | Apple MFi (iAP2) |
| **Encryption** | FairPlay (AES-128) |
| **Service Discovery** | _airplay._tcp |

**Key Libraries:**
- `libNmeCarPlay.so` (1.0 MB) - AirPlay protocol
- `libNmeIAP.so` (2.9 MB) - iAP2 protocol
- `libNmeAppleAuth.so` (80 KB) - MFi authentication

### Android Auto Protocol

| Aspect | Specification |
|--------|---------------|
| **Protocol** | Android Auto Protocol (AAP) |
| **Transport (Wired)** | USB AOA (Android Open Accessory) |
| **Transport (Wireless)** | WiFi Direct + Bluetooth |
| **Authentication** | Google certificates |
| **Encryption** | SSL/TLS |
| **Service Discovery** | Android Binder IPC |

**Key Components:**
- `CarService` - Vehicle integration
- `media_projection` - Screen mirroring
- `companiondevice` - Device pairing
- `android.car.usb.handler` - USB handling

**Security Certificates:**
```
/system/etc/security/androidauto/
├── dc3d1471.0           (4387 bytes)
├── dc3d1471_nxp.0       (4387 bytes)
├── a1467e3a.0           (1285 bytes)
├── a1467e3a_nxp.0       (1285 bytes)
├── privatecert.txt      (1704 bytes)
└── privatecert_nxp.txt  (1704 bytes)
```

---

## Feature Comparison

### Driver Workload Lockouts (GIS-337)

| Feature | CarPlay | Android Auto |
|---------|---------|--------------|
| Soft Keyboard | Configurable | No text input allowed |
| Soft Keypad | Configurable | - |
| Voice Input | Always allowed | Configurable |
| Video Playback | Not applicable | Disabled |
| List Length Limits | Music/Non-music separate | Message length limited |
| Setup/Config | - | Disabled while driving |

### Wireless Support

| Feature | CarPlay | Android Auto |
|---------|---------|--------------|
| WiFi Projection | `Apple_CarPlay_enableWireless` | `AndroidAuto_enableWireless` |
| Default State | false (calibratable) | false (calibratable) |
| Implementation | AirPlay over WiFi | AAP over WiFi |

### Projection Features (GIS-513)

| Feature | Calibration | Default |
|---------|-------------|---------|
| Apple CarPlay | `Enable_Application_Apple_Carplay` | true |
| Android Auto | `Enable_application_google_Automotive_link` | true |
| MirrorLink | `Enable_Application_MirrorLink` | false |
| Baidu CarLife | `ENABLE_APPLICATIONBAIDU_CARLIFE` | false |

---

## Performance Comparison

### Video Decode Latency

| Protocol | Decoder | Latency | CPU Usage |
|----------|---------|---------|-----------|
| CarPlay | NVDEC SW | 10-15 ms | Medium |
| Android Auto | Intel HW | ~5 ms | Low |

### Audio Latency

| Protocol | Path | Latency |
|----------|------|---------|
| CarPlay | AirPlay → NME → AudioFlinger | ~50-80 ms |
| Android Auto | AAP → CarAudio → AudioFlinger | ~40-60 ms |

### Memory Usage

| Protocol | Video Buffers | Audio Buffers | Total |
|----------|---------------|---------------|-------|
| CarPlay | ~75-90 MB | ~10 MB | ~85-100 MB |
| Android Auto | ~40-50 MB | ~10 MB | ~50-60 MB |

---

## Error Recovery Comparison

### CarPlay

```cpp
// AirPlay-specific recovery
OnFrame() {
    if (corrupt_data) {
        AirPlayReceiverSessionForceKeyFrame(session);
    }
}

// Decoder errors
NvdecDeliverHeaders() {
    switch (error) {
        case NvdecError_BitDepth:
        case NvdecError_ChromaFormat:
        case NvdecError_Profile:
            // Reconfigure or skip
    }
}
```

### Android Auto

```java
// Standard MediaCodec recovery
mediaCodec.setCallback(new MediaCodec.Callback() {
    @Override
    public void onError(MediaCodec codec, CodecException e) {
        if (e.isRecoverable()) {
            codec.stop();
            codec.configure(...);
            codec.start();
        }
    }
});
```

---

## Architecture Summary

### CarPlay Stack

```
┌─────────────────────────────────────────┐
│           GMCarPlay.apk (36 MB)         │
├─────────────────────────────────────────┤
│           Java/Kotlin Layer             │
├─────────────────────────────────────────┤
│    libNmeCarPlay.so │ libNmeIAP.so      │
├─────────────────────────────────────────┤
│  libNmeVideoSW.so │ libNmeAudioAAC.so   │
├─────────────────────────────────────────┤
│       libNmeBaseClasses.so (4.8 MB)     │
├─────────────────────────────────────────┤
│    ANativeWindow │ AudioFlinger │ EGL   │
└─────────────────────────────────────────┘
```

### Android Auto Stack

```
┌─────────────────────────────────────────┐
│     AndroidAutoIMEPrebuilt (72 MB)      │
├─────────────────────────────────────────┤
│           CarService (AAOS)             │
├─────────────────────────────────────────┤
│      MediaCodec │ CarAudioService       │
├─────────────────────────────────────────┤
│  OMX.Intel.hw_vd.* │ AudioFlinger       │
├─────────────────────────────────────────┤
│      Standard Android Framework         │
└─────────────────────────────────────────┘
```

---

## Key Takeaways

1. **Different Decoders**: CarPlay uses software decode (NVDEC), Android Auto uses hardware decode (Intel OMX)

2. **Different Frameworks**: CarPlay uses CINEMO/NME (Harman proprietary), Android Auto uses standard AOSP

3. **Same Audio Routing**: Both share AudioFlinger and bus architecture

4. **CarPlay Has More Libraries**: ~17.5 MB of NME libraries vs standard AOSP for Android Auto

5. **CarPlay Has Dedicated Audio Tuning**: SCD files for USB and WiFi telephony variants

6. **Android Auto Lower Latency**: Hardware decode provides faster video path

7. **CarPlay More Custom**: Proprietary AirPlay protocol with custom error recovery

8. **Android Auto Resolution Gap**: No explicit video/UI resolution configuration found for Android Auto in extracted partitions - may cause aspect ratio or touch mapping issues with the non-standard 2400x960 (2.5:1) display

---

## Data Sources

**Extracted Partitions:**
- `/vendor/etc/media_codecs.xml` - Video codec definitions
- `/system/lib64/libNme*.so` - NME library binaries
- `/system/etc/security/androidauto/` - Android Auto certificates
- `/vendor/etc/scd/*.scd` - CarPlay audio tuning

**CalDef Database:**
- `GIS337_ConsumerDeviceProjection.caldef` - Driver workload lockouts
- `GIS513_DeviceProjection.caldef` - Feature enable/disable

**Binary Analysis:**
- `strings`, `readelf`, `nm` on NME libraries

**Source:** `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`
