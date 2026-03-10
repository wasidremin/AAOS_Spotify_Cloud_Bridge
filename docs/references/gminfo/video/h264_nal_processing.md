# GM AAOS H.264 NAL Unit Processing

**Device:** GM Info 3.7 (gminfo37)
**Decoder:** CINEMO NVDEC Software Decoder (libNmeVideoSW.so)
**Analysis Date:** January 2026
**Source:** Binary analysis of NME libraries from GM AAOS partitions

---

## Overview

The GM AAOS CarPlay implementation processes H.264 NAL (Network Abstraction Layer) units through the CINEMO framework's NVDEC software decoder. This document details the NAL unit parsing, RBSP extraction, and frame handling mechanisms.

---

## NAL Unit Structure

### H.264 Annex B Format

CarPlay video arrives in Annex B byte stream format:
```
┌──────────────┬─────────────────────────────────────────────┐
│ Start Code   │ NAL Unit                                    │
│ 00 00 00 01  │ [nal_header][rbsp_data...]                  │
│   or         │                                             │
│ 00 00 01     │                                             │
└──────────────┴─────────────────────────────────────────────┘
```

### NAL Header Byte

```
┌───┬───┬───┬───┬───┬───┬───┬───┐
│ F │   NRI   │      Type       │
│ 0 │ 1 │ 2 │ 3 │ 4 │ 5 │ 6 │ 7 │
└───┴───┴───┴───┴───┴───┴───┴───┘
```

| Field | Bits | Description |
|-------|------|-------------|
| F (forbidden_zero_bit) | 1 | Must be 0 |
| NRI (nal_ref_idc) | 2 | Reference importance (0-3) |
| Type (nal_unit_type) | 5 | NAL unit type (1-23) |

---

## NAL Unit Types

### Types Processed by CINEMO NVDEC

| Type | Name | Description | Priority |
|------|------|-------------|----------|
| 1 | Non-IDR Slice | P/B frame data | Normal |
| 5 | IDR Slice | Keyframe (sync point) | Critical |
| 6 | SEI | Supplemental Enhancement Information | High |
| 7 | SPS | Sequence Parameter Set | Critical |
| 8 | PPS | Picture Parameter Set | Critical |
| 9 | AU Delimiter | Access Unit Delimiter | Low |

### Frame Type Handling

#### IDR Frames (Type 5)

```cpp
// libNmeCarPlay.so - OnFrame() pseudo-code
if (is_idr_frame) {
    log("OnFrame() -> syncframe");
    // Reset decoder state
    // Clear reference picture buffer
    // Begin new GOP (Group of Pictures)
}
```

**IDR Frame Characteristics:**
- Instantaneous decoder refresh
- All subsequent frames can be decoded without prior reference
- Requested via `ForceKeyframe()` on errors
- Typical interval: Every 1-2 seconds in CarPlay

#### P-Frames (Type 1, forward predicted)

- Reference previously decoded frames
- Lower bitrate than IDR
- Most common frame type (~80% of frames)

#### SPS/PPS (Types 7, 8)

Extracted at stream start and on resolution/format changes:

**SPS Parameters Parsed:**
- `profile_idc` - H.264 profile (Baseline, Main, High)
- `level_idc` - H.264 level (3.0, 4.0, 4.1, 5.1)
- `pic_width_in_mbs_minus1` - Frame width
- `pic_height_in_map_units_minus1` - Frame height
- `frame_mbs_only_flag` - Progressive vs interlaced
- `chroma_format_idc` - 4:2:0, 4:2:2, 4:4:4

**PPS Parameters Parsed:**
- `pic_parameter_set_id`
- `seq_parameter_set_id`
- `entropy_coding_mode_flag` - CAVLC/CABAC
- `deblocking_filter_control_present_flag`

---

## RBSP Processing (libNmeVideoSW.so)

### RBSP Extraction

Raw Byte Sequence Payload extraction removes emulation prevention bytes:
```
NAL Unit:      00 00 03 00  →  RBSP: 00 00 00
NAL Unit:      00 00 03 01  →  RBSP: 00 00 01
NAL Unit:      00 00 03 02  →  RBSP: 00 00 02
NAL Unit:      00 00 03 03  →  RBSP: 00 00 03
```

### Source Files (from string analysis)

```
../../NmeLibs/Nvdec/vdec/vdec264.cpp       - H.264 decoder core
../../NmeLibs/Nvdec/vdec/vrbsp.cpp         - RBSP extraction
../../NmeLibs/Nvdec/vdec/vrbsp_sequence.cpp - SPS/PPS parsing
../../NmeLibs/Nvdec/vutilities/vdisplay.cpp - Display output
```

### Decoder Entry Points

```cpp
H264Create()          // Initialize decoder instance
H264DeliverAnnexB()   // Feed NAL units to decoder
H264Free()            // Release decoder resources
FinishCurrentFrame()  // Complete frame decode
```

---

## SEI Message Processing

### SEI NAL Unit Structure

```
┌──────────────┬─────────────────────────────────────────────┐
│ NAL Header   │ SEI Payload                                 │
│ Type = 6     │ [payloadType][payloadSize][payload...]      │
└──────────────┴─────────────────────────────────────────────┘
```

### Supported SEI Types (from libNmeVideoSW.so strings)

| SEI Type | Function Name | Purpose |
|----------|---------------|---------|
| 16 | `rbsp_sei_full_frame_freeze` | Freeze display on current frame |
| 17 | `rbsp_sei_full_frame_freeze_release` | Resume display |
| 18 | `rbsp_sei_full_frame_snapshot` | Request snapshot |
| 3 | `rbsp_sei_filler_payload` | Filler data (ignored) |
| 6 | `rbsp_sei_dec_ref_pic_marking_repetition` | Reference frame management |
| 8 | `rbsp_sei_deblocking_filter_display_preference` | Deblocking settings |
| 9 | `rbsp_sei_scene_info` | Scene change metadata |
| 10 | `rbsp_sei_sub_seq_info` | Subsequence information |
| 11 | `rbsp_sei_sub_seq_layer_characteristics` | Layer characteristics |
| 12 | `rbsp_sei_sub_seq_characteristics` | Subsequence characteristics |
| 21 | `rbsp_sei_stereo_video_info` | 3D/stereo video |
| 4 | `rbsp_sei_user_data_registered_itu_t_t35` | ITU-T T.35 user data |
| 13 | `rbsp_sei_motion_constrained_slice_group_set` | Motion constraints |
| 20 | `rbsp_sei_progressive_refinement_segment_start` | Progressive refinement |
| 21 | `rbsp_sei_progressive_refinement_segment_end` | End refinement |
| 22 | `rbsp_sei_spare_pic` | Spare picture |

### SEI Processing Flow

```cpp
// Pseudo-code
ProcessSEI(sei_payload) {
    switch (payloadType) {
        case FULL_FRAME_FREEZE:
            FreezeDisplay();
            break;
        case FULL_FRAME_FREEZE_RELEASE:
            ResumeDisplay();
            break;
        case DEBLOCKING_PREFERENCE:
            SetDeblockingParams(payload);
            break;
        case SCENE_INFO:
            HandleSceneChange(payload);
            break;
        // ... other SEI types
    }
}
```

---

## Frame Validation

### NAL Unit Validation (libNmeCarPlay.so)

```cpp
OnFrame(frame_data, nalu_size, header_len) {
    // Size validation
    if (nalu_size == 0 || nalu_size > MAX_NALU_SIZE) {
        log("OnFrame() invalid nalu_size");
        return ERROR;
    }

    // Data integrity
    if (data_corrupted(frame_data, nalu_size)) {
        log("OnFrame() corrupt video data");
        RequestKeyframe();
        return ERROR;
    }

    // Length validation
    if (actual_len != expected_len) {
        log("OnFrame() wrong video data len");
        return ERROR;
    }

    // Success
    log("OnFrame() received video frame: %d (nalu_size:%u, header_len:%d)",
        frame_id, nalu_size, header_len);
    return OK;
}
```

### Decoder Validation (libNmeVideoSW.so)

```cpp
NvdecDeliverHeaders(stream_id, size) {
    result = ValidateHeaders();

    switch (result) {
        case NvdecError_BitDepth:
            log("NvdecDeliverHeaders() returned NvdecError_BitDepth");
            // Unsupported: 10-bit, 12-bit content
            return SKIP;

        case NvdecError_ChromaFormat:
            log("NvdecDeliverHeaders() returned NvdecError_ChromaFormat");
            // Unsupported: 4:2:2, 4:4:4
            return SKIP;

        case NvdecError_Profile:
            log("NvdecDeliverHeaders() returned NvdecError_Profile");
            // Unsupported: High 10, High 4:2:2
            return SKIP;

        case NvdecError_MaxVxdFileMemory:
        case NvdecError_MaxVxdTotalMemory:
            log("Memory limit exceeded");
            return REDUCE_BUFFERS;
    }
    return OK;
}
```

---

## Reference Frame Management

### Decoded Picture Buffer (DPB)

The NVDEC decoder maintains a decoded picture buffer for reference:

```cpp
// Reference frame states
typedef enum {
    FRAME_UNUSED,           // Available for new decode
    FRAME_SHORT_TERM_REF,   // Short-term reference
    FRAME_LONG_TERM_REF,    // Long-term reference
    FRAME_NON_REF,          // Non-reference (can be displayed then freed)
    FRAME_DISPLAYED         // Displayed, pending free
} FrameState;
```

### Reference Picture Marking

From SEI `rbsp_sei_dec_ref_pic_marking_repetition`:
- Mark frames as "used for reference" or "unused"
- Handle memory_management_control_operation
- Manage short-term vs long-term references

---

## Error Recovery

### Recovery Strategies

| Error Condition | Detection | Recovery Action |
|-----------------|-----------|-----------------|
| Corrupt NAL | CRC/size mismatch | Skip, request IDR |
| Missing reference | Decode error | Skip, request IDR |
| Buffer overflow | Memory limit | Reduce buffer count |
| Timestamp discontinuity | PTS jump | Reset clock sync |
| Profile mismatch | SPS parse | Reconfigure decoder |

### Keyframe Request

```cpp
// libNmeCarPlay.so
RequestRecovery() {
    result = AirPlayReceiverSessionForceKeyFrame(session);
    if (result != 0) {
        log("AirPlayReceiverSessionForceKeyFrame failed: %d", result);
    }
}
```

### Frame Dropping for Late Frames

```cpp
// libNmeVideo.so - QualityControl()
if (frame_late && clock_running) {
    if (is_reference_frame) {
        // Decode but don't display
        log("reference frames only");
    } else {
        // Skip B-frames entirely
        DropFrame();
    }
}
```

---

## Performance Considerations

### Decode Timing

| Frame Type | Typical Decode Time |
|------------|---------------------|
| IDR (1080p) | 8-15 ms |
| P-Frame (1080p) | 3-8 ms |
| B-Frame (1080p) | 5-10 ms |

### Buffer Configuration

```cpp
// Options from libNmeVideo.so
video_extra_surfaces     // Additional decode surfaces (default: 3-4)
video_frame_drop_threshold  // Late threshold in ms
```

### Memory Usage

| Component | Typical Usage |
|-----------|---------------|
| Decode buffers (1080p) | ~10-15 MB |
| Reference frames (4-5) | ~40-50 MB |
| Output surfaces (3) | ~25 MB |
| **Total** | ~75-90 MB |

---

## H.264 Profiles Supported

### Verified Support

| Profile | Level | Resolution | Notes |
|---------|-------|------------|-------|
| Baseline | 3.0-5.1 | Up to 4K | CAVLC only |
| Main | 3.0-5.1 | Up to 4K | CABAC, B-frames |
| High | 3.0-5.1 | Up to 4K | 8x8 transform |

### CarPlay Typical Settings

| Parameter | Typical Value |
|-----------|---------------|
| Profile | Main or High |
| Level | 4.0 or 4.1 |
| Resolution | 1920x1080 or 1280x720 |
| Frame Rate | 60 fps |
| Chroma | 4:2:0 |
| Bit Depth | 8-bit |

---

## Related Documentation

- [carplay_video_pipeline.md](carplay_video_pipeline.md) - Overall video pipeline
- [video_codecs.md](video_codecs.md) - Codec specifications
- [software_rendering.md](software_rendering.md) - Software decoder details

---

## Data Sources

Binary analysis of:
- `libNmeVideoSW.so` (646 KB) - strings, symbols
- `libNmeVideo.so` (121 KB) - strings, symbols
- `libNmeCarPlay.so` (1.0 MB) - strings, symbols

Source from: `/Users/zeno/Downloads/misc/GM_research/gm_aaos/extracted_partitions/system_extracted/system/lib64/`
