# Video PTS Timing Strategies

**Document Type:** Implementation Analysis
**Platform:** Android MediaCodec (applicable to GM AAOS and general Android)
**Analysis Date:** January 2026
**Source:** carlink_native video pipeline comparison study

---

## Overview

Presentation Time Stamps (PTS) are critical for proper video frame ordering and audio/video synchronization. This document compares two approaches for generating PTS values when decoding video streams:

1. **Source PTS Extraction** - Extract timestamps from incoming video packet headers
2. **Synthetic Monotonic PTS** - Generate timestamps using a counter and assumed frame rate

---

## Approach 1: Source PTS Extraction

Extracts the presentation timestamp from the video packet header provided by the source (e.g., CarPlay adapter, streaming server).

### Implementation Example

```java
// Video header structure (20 bytes for CPC200-CCPA adapter):
// offset 0:  width (4 bytes, little-endian)
// offset 4:  height (4 bytes, little-endian)
// offset 8:  encoderState (4 bytes)
// offset 12: pts (4 bytes, little-endian, milliseconds)
// offset 16: flags (4 bytes)

// Extract PTS from header
public int extractPtsFromHeader(byte[] buffer, int offset) {
    return (buffer[offset + 12] & 0xFF) |
           ((buffer[offset + 13] & 0xFF) << 8) |
           ((buffer[offset + 14] & 0xFF) << 16) |
           ((buffer[offset + 15] & 0xFF) << 24);
}

// Queue for correlating PTS to frames (thread-safe)
private final ConcurrentLinkedQueue<Long> sourcePtsQueue = new ConcurrentLinkedQueue<>();

// On receiving video data
public void processVideoData(int payloadLength, int sourcePtsMs) {
    // Convert ms to µs for MediaCodec
    long sourcePtsUs = sourcePtsMs * 1000L;
    sourcePtsQueue.offer(sourcePtsUs);
    // ... write to ring buffer
}

// On feeding MediaCodec
Long sourcePts = sourcePtsQueue.poll();
long pts;
if (sourcePts != null && sourcePts >= 0) {
    pts = sourcePts;  // Use source PTS
} else {
    pts = syntheticPts.getAndAdd(frameDurationUs);  // Fallback
}
codec.queueInputBuffer(index, 0, dataSize, pts, 0);
```

### Frame Drop Detection (Bonus Feature)

```java
private static final int FRAME_DROP_THRESHOLD_MS = 50;
private static final int IDR_REQUEST_GAP_THRESHOLD_MS = 500;

private void detectFrameDrops(int currentPtsMs) {
    if (lastSourcePtsMs > 0 && currentPtsMs > 0) {
        long gap = currentPtsMs - lastSourcePtsMs;

        // Detect dropped frames (gap > 1.5 frames at 30fps)
        if (gap > FRAME_DROP_THRESHOLD_MS) {
            int missedFrames = (int) (gap / 33) - 1;
            droppedFrameCount += missedFrames;
            log("Frame drop detected: gap=" + gap + "ms, ~" + missedFrames + " frames");
        }

        // Request IDR after large gaps (video likely corrupted)
        if (gap > IDR_REQUEST_GAP_THRESHOLD_MS) {
            requestKeyframe();
        }
    }
    lastSourcePtsMs = currentPtsMs;
}
```

---

## Approach 2: Synthetic Monotonic PTS

Generates timestamps using a monotonically increasing counter based on assumed frame rate.

### Implementation Example

```java
// Constants
private static final long DEFAULT_FRAME_DURATION_US = 16667; // ~60fps (1000000/60)
private final AtomicLong presentationTimeUs = new AtomicLong(0);
private volatile long frameDurationUs = DEFAULT_FRAME_DURATION_US;

// Configure from adapter-reported FPS
public void setTargetFps(int fps) {
    if (fps > 0 && fps <= 120) {
        frameDurationUs = 1_000_000L / fps;
    }
}

// On feeding MediaCodec - always increment by frame duration
long pts = presentationTimeUs.getAndAdd(frameDurationUs);
codec.queueInputBuffer(index, 0, dataSize, pts, 0);

// Reset on codec restart
public void reset() {
    presentationTimeUs.set(0);
    // ...
}
```

---

## Comparison Table

| Aspect | Source PTS | Synthetic Monotonic |
|--------|------------|---------------------|
| **A/V Sync Accuracy** | High (matches source) | Medium (assumes constant rate) |
| **Frame Drop Detection** | Yes (via PTS gaps) | No |
| **Implementation Complexity** | Higher | Lower |
| **CPU Overhead** | Slightly higher | Minimal |
| **Source Dependency** | Requires valid PTS in stream | Works with any source |
| **Variable Frame Rate** | Handles correctly | Causes drift |
| **Debugging Value** | High (reveals upstream issues) | Low |

---

## Detailed Pros and Cons

### Source PTS Extraction

#### Pros

| Benefit | Explanation |
|---------|-------------|
| **Accurate A/V synchronization** | Timestamps reflect actual capture time from the source device (e.g., iPhone). Essential for lip-sync in video calls and music video playback. |
| **Frame drop detection** | PTS gaps reveal when frames were lost in transit. A 50ms gap at 30fps indicates ~1-2 missed frames. |
| **Intelligent error recovery** | Large PTS gaps (>500ms) can automatically trigger IDR frame requests, improving recovery time after packet loss. |
| **FPS-independent operation** | Works correctly regardless of actual frame rate - handles 24fps, 30fps, 60fps, or variable frame rate without configuration. |
| **Superior diagnostics** | PTS discontinuities in logs help identify USB transfer issues, adapter problems, WiFi interference, or encoder stalls. |
| **Handles variable bitrate** | Source may send frames at varying intervals (scene complexity). Source PTS maintains correct timing. |

#### Cons

| Drawback | Explanation |
|----------|-------------|
| **Queue synchronization overhead** | Requires thread-safe queue (ConcurrentLinkedQueue) to correlate PTS values to frames as they flow through the pipeline. |
| **Header parsing per frame** | Must extract 4-byte little-endian integer from every video packet header. |
| **Source dependency** | Requires the video source to provide valid, monotonically increasing PTS values. Invalid source = invalid playback. |
| **PTS wraparound handling** | 32-bit millisecond PTS wraps after ~49 days. Must handle wraparound for long sessions. |
| **Complex error handling** | Must gracefully handle missing PTS (-1), out-of-order PTS, or corrupted values. |
| **Memory for queue** | Queue grows if decoder stalls while data keeps arriving. Requires bounds management. |

---

### Synthetic Monotonic PTS

#### Pros

| Benefit | Explanation |
|---------|-------------|
| **Simple implementation** | No header parsing, no queue, no synchronization. Just increment a counter. |
| **Zero parsing overhead** | No per-frame extraction cost. Counter increment is single atomic operation. |
| **Source-independent** | Works with any video source regardless of whether it provides timestamps. Useful for raw H.264 streams. |
| **Guaranteed monotonic** | Never produces backward jumps or gaps (unless explicitly reset). MediaCodec requires monotonic PTS. |
| **No external dependencies** | Self-contained - doesn't rely on adapter firmware, protocol version, or header format. |
| **Predictable behavior** | Timing is deterministic based on configured FPS. Easy to reason about and debug. |

#### Cons

| Drawback | Explanation |
|----------|-------------|
| **No A/V sync accuracy** | Timestamps don't reflect actual capture time. Audio recorded at different time than video will drift. |
| **No frame drop detection** | Cannot detect when frames are lost. Decoder just waits for next frame with no visibility into gaps. |
| **FPS assumption errors** | If actual stream is 30fps but configured for 60fps, playback runs 2x speed. If 60fps configured as 30fps, stutters. |
| **No intelligent recovery** | Cannot detect large gaps requiring IDR request. Must rely on periodic keyframe timers or error callbacks only. |
| **Variable frame rate failure** | Streams with variable frame rate (VFR) will have incorrect timing. Common in screen recordings and game capture. |
| **Drift over time** | Small timing errors accumulate. After hours of playback, A/V sync may be noticeably off. |
| **No diagnostic value** | Logs show only synthetic values, masking upstream timing problems that could help debug issues. |

---

## Decision Matrix

| Use Case | Recommended Approach | Rationale |
|----------|---------------------|-----------|
| **CarPlay/Android Auto projection** | Source PTS | A/V sync critical for calls; frame drop detection aids recovery |
| **Live streaming (RTSP/HLS)** | Source PTS | Stream provides PTS; variable bitrate common |
| **Local file playback** | Source PTS | Container (MP4/MKV) has accurate PTS |
| **Raw H.264 over USB (no header)** | Synthetic | No PTS available in stream |
| **Test/debug playback** | Synthetic | Simpler; timing less critical |
| **Game streaming** | Source PTS | Low latency requires accurate timing; VFR common |
| **Security camera feeds** | Either | Depends on camera firmware capabilities |

---

## Hybrid Approach (Recommended)

The optimal implementation uses **Source PTS with Synthetic Fallback**:

```java
// Primary: Use source PTS when available
Long sourcePts = sourcePtsQueue.poll();
long pts;

if (useSourcePts && sourcePts != null && sourcePts >= 0) {
    // Source PTS available and valid
    pts = sourcePts;
} else {
    // Fallback to synthetic (source unavailable or invalid)
    pts = syntheticPts.getAndAdd(frameDurationUs);
}

codec.queueInputBuffer(index, 0, dataSize, pts, 0);
```

This provides:
- Best-case accuracy when source PTS is available
- Graceful degradation when source PTS is missing or corrupt
- Backward compatibility with streams lacking timestamps

---

## GM AAOS Specific Considerations

### Intel VPU Interaction

The Intel hardware decoder (`OMX.Intel.hw_vd.h264`) on GM AAOS gminfo37:
- Requires monotonically increasing PTS (same as all MediaCodec decoders)
- Does not perform PTS-based frame reordering (B-frames handled by VPU internally)
- Outputs frames in decode order with original PTS preserved

### CarPlay Adapter Behavior

The CPC200-CCPA adapter:
- Provides valid PTS in video header at offset 12 (milliseconds, little-endian)
- PTS reflects iPhone capture time
- PTS is monotonically increasing under normal operation
- Large PTS gaps indicate WiFi/USB packet loss

### Recommended Configuration for GM AAOS

```java
// Enable source PTS for CarPlay projection
private volatile boolean useSourcePts = true;

// Frame drop thresholds tuned for automotive
private static final int FRAME_DROP_THRESHOLD_MS = 50;    // ~1.5 frames at 30fps
private static final int IDR_REQUEST_GAP_THRESHOLD_MS = 500;  // Request keyframe after 0.5s gap

// Fallback FPS for synthetic mode
private static final long DEFAULT_FRAME_DURATION_US = 16667;  // 60fps default
```

---

## References

- Android MediaCodec Documentation: PTS requirements for queueInputBuffer()
- H.264/AVC Specification: Annex D (Timing and HRD)
- carlink_native source analysis: H264Renderer.java, UsbDeviceWrapper.kt
- GM AAOS research: `/Users/zeno/Downloads/misc/GM_research/gm_aaos/`
