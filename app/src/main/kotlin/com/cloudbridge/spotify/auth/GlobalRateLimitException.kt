package com.cloudbridge.spotify.auth

import java.io.IOException

class GlobalRateLimitException(
    val lockedUntilEpochMs: Long,
    val retryAfterSeconds: Long
) : IOException("Spotify rate limit lockout active for ${retryAfterSeconds}s")