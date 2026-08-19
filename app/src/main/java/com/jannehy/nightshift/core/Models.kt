package com.jannehy.nightshift.core

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class NightshiftUser(val username: String, val role: String = "user") {
    val isAdmin: Boolean get() = role == "admin"
}

/** `/api/me` – everything the UI needs to decide what to show. */
@Serializable
data class MeInfo(
    val user: NightshiftUser,
    @SerialName("is_admin") val isAdmin: Boolean = false,
    @SerialName("sync_enabled") val syncEnabled: Boolean = false,
    @SerialName("navidrome_enabled") val navidromeEnabled: Boolean = false,
    @SerialName("nightly_schedule") val nightlySchedule: String? = null,
)

@Serializable
data class HealthInfo(val status: String, val configured: Boolean, val version: String? = null)

/** `/api/version` – open, so a client can identify a server before signing in. */
@Serializable
data class VersionInfo(val name: String? = null, val version: String) {
    val isSupported: Boolean get() = compare(version, MINIMUM) >= 0

    companion object {
        /** Oldest server this build understands: before 1.3 the download log
         *  is shared between all users. */
        const val MINIMUM = "1.3"

        fun compare(lhs: String, rhs: String): Int {
            val l = lhs.split(".").map { it.toIntOrNull() ?: 0 }
            val r = rhs.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(l.size, r.size)) {
                val a = l.getOrElse(i) { 0 }
                val b = r.getOrElse(i) { 0 }
                if (a != b) return if (a < b) -1 else 1
            }
            return 0
        }
    }
}

@Serializable
data class LoginResponse(val ok: Boolean = false, val user: NightshiftUser)

@Serializable
data class JobRef(
    @SerialName("job_id") val jobId: String,
    val position: Int = 0,
    @SerialName("track_count") val trackCount: Int? = null,
)

/** One server-sent event from `/stream/<job_id>`. */
@Serializable
data class JobEvent(
    val type: String,
    val message: String? = null,
    val line: String? = null,
    val progress: Int? = null,
    val current: Int? = null,
    val total: Int? = null,
    val track: String? = null,
    @SerialName("total_tracks") val totalTracks: Int? = null,
) {
    val isTerminal: Boolean get() = type == "done" || type == "error"

    val logLine: String?
        get() = when {
            !line.isNullOrEmpty() -> line
            type == "progress" && !track.isNullOrEmpty() -> "♪ $track"
            !message.isNullOrEmpty() -> message
            else -> null
        }
}

/** `/download-log` and `/nightly-log` – lets the UI restore after a cold start. */
@Serializable
data class LogState(
    val log: String = "",
    val mtime: Double = 0.0,
    val finished: Boolean = false,
    val failed: Boolean = false,
    val running: Boolean = false,
) {
    val lines: List<String> get() = log.split("\n")
}

@Serializable
data class QueueEntry(@SerialName("job_id") val jobId: String? = null, val label: String = "")

@Serializable
data class QueueStatus(val running: QueueEntry? = null, val pending: List<QueueEntry> = emptyList()) {
    val count: Int get() = (if (running == null) 0 else 1) + pending.size
    val isEmpty: Boolean get() = running == null && pending.isEmpty()
}

@Serializable
data class NightlyStatus(val running: Boolean = false)

enum class SearchEntity(val value: String) { SONG("song"), ALBUM("album"), ARTIST("musicArtist") }

@Serializable
data class SearchResult(
    val kind: String,
    val id: Long? = null,
    val title: String = "",
    val artist: String = "",
    val album: String? = null,
    val artwork: String? = null,
    val preview: String? = null,
    @SerialName("duration_ms") val durationMs: Long? = null,
    @SerialName("release_date") val releaseDate: String? = null,
    @SerialName("track_count") val trackCount: Int? = null,
    val genre: String? = null,
    val query: String = "",
) {
    val stableId: String get() = id?.toString() ?: "$kind:$query"

    val durationText: String?
        get() = durationMs?.takeIf { it > 0 }?.let {
            val seconds = it / 1000
            "%d:%02d".format(seconds / 60, seconds % 60)
        }

    val releaseYear: String? get() = releaseDate?.takeIf { it.length >= 4 }?.substring(0, 4)
}

@Serializable
data class SearchResponse(val results: List<SearchResult> = emptyList(), val error: String? = null)

@Serializable
data class SyncItem(
    val url: String? = null,
    val source: String = "spotify",
    val name: String = "?",
    val file: String? = null,
    val owner: String? = null,
    @SerialName("public") val isPublic: Boolean = true,
    @SerialName("can_remove") val canRemove: Boolean = false,
) {
    val id: String get() = url?.takeIf { it.isNotEmpty() } ?: file ?: name

    val sourceLabel: String
        get() = when (source) {
            "spotify" -> "Spotify"
            "soundcloud" -> "SoundCloud"
            "youtube" -> "YouTube"
            else -> source.replaceFirstChar { it.uppercase() }
        }
}

@Serializable
data class SyncListResponse(val entries: List<SyncItem> = emptyList(), val enabled: Boolean = false)

@Serializable
data class NavidromeUser(val id: String, val userName: String = "", val name: String = "")

@Serializable
data class NavidromeUsersResponse(
    val users: List<NavidromeUser> = emptyList(),
    val enabled: Boolean = false,
    val error: String? = null,
)

@Serializable
data class UsersResponse(val users: List<NightshiftUser> = emptyList())
