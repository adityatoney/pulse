package com.pulse.data.cloud.backup

import android.util.Log
import com.pulse.data.cloud.DriveAuthManager
import com.pulse.data.local.PulseDatabase
import com.pulse.domain.util.Clock
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "DriveBackup"
private const val DRIVE_API_BASE = "https://www.googleapis.com"
private const val BACKUP_FILE_NAME = "pulse_backup.json.gz"

@Singleton
class DriveBackupManager @Inject constructor(
    private val httpClient: HttpClient,
    private val driveAuth: DriveAuthManager,
    private val db: PulseDatabase,
    private val clock: Clock,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }

    suspend fun backup(): Result<BackupMetadata> = runCatching {
        val token = driveAuth.getAccessToken()
            ?: throw IllegalStateException("Not authenticated with Google Drive")

        Log.d(TAG, "Starting backup")

        // 1. Read all tables
        val payload = withContext(Dispatchers.IO) {
            BackupPayload(
                version = 1,
                dbVersion = PulseDatabase.VERSION,
                appVersion = "1.0.0",
                createdAtMs = clock.now().toEpochMilliseconds(),
                dailyAggregates = db.dailyAggregateDao().getAll(),
                exerciseSessions = db.exerciseSessionDao().getAll(),
                exerciseHrSamples = db.exerciseHrSampleDao().getAll(),
                exerciseLaps = db.exerciseLapDao().getAll(),
                exerciseRoutePoints = db.exerciseRoutePointDao().getAll(),
                healthSamples = db.healthSampleDao().getAll(),
                sleepSessions = db.sleepSessionDao().getAll(),
                syncState = db.syncStateDao().getAll(),
                goals = db.goalDao().getAll(),
            )
        }

        // 2. Serialize + GZIP compress
        val compressed = withContext(Dispatchers.IO) {
            val jsonStr = json.encodeToString(BackupPayload.serializer(), payload)
            ByteArrayOutputStream().use { baos ->
                GZIPOutputStream(baos).use { gzip ->
                    gzip.write(jsonStr.toByteArray(Charsets.UTF_8))
                }
                baos.toByteArray()
            }
        }
        Log.d(TAG, "Backup payload compressed: ${compressed.size} bytes")

        // 3. Upload to Drive
        val existing = findBackup()
        if (existing != null) {
            updateFile(existing.fileId, compressed, token)
        } else {
            createFile(compressed, token)
        }
    }

    suspend fun findBackup(): BackupMetadata? {
        val token = driveAuth.getAccessToken() ?: return null
        return try {
            val response: DriveFileListResponse = httpClient.get(
                "$DRIVE_API_BASE/drive/v3/files"
            ) {
                header("Authorization", "Bearer $token")
                url {
                    parameters.append("spaces", "appDataFolder")
                    parameters.append("q", "name='$BACKUP_FILE_NAME'")
                    parameters.append("fields", "files(id,name,modifiedTime,size)")
                    parameters.append("pageSize", "1")
                }
            }.body()

            response.files.firstOrNull()?.let { file ->
                BackupMetadata(
                    fileId = file.id,
                    modifiedTime = file.modifiedTime ?: "",
                    sizeBytes = file.size?.toLongOrNull() ?: 0L,
                )
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to find backup: ${e.message}")
            null
        }
    }

    suspend fun restore(): Result<Int> = runCatching {
        val token = driveAuth.getAccessToken()
            ?: throw IllegalStateException("Not authenticated with Google Drive")
        val backup = findBackup()
            ?: throw IllegalStateException("No backup found in Drive")

        Log.d(TAG, "Starting restore from ${backup.fileId}")

        // 1. Download
        val compressed: ByteArray = httpClient.get(
            "$DRIVE_API_BASE/drive/v3/files/${backup.fileId}?alt=media"
        ) {
            header("Authorization", "Bearer $token")
        }.body<ByteArray>()

        // 2. Decompress + deserialize
        val payload = withContext(Dispatchers.IO) {
            val jsonStr = GZIPInputStream(ByteArrayInputStream(compressed)).use { gzip ->
                gzip.readBytes().toString(Charsets.UTF_8)
            }
            json.decodeFromString(BackupPayload.serializer(), jsonStr)
        }

        // 3. Clear and repopulate all tables (parent tables before children for FK ordering)
        withContext(Dispatchers.IO) {
            db.exerciseSessionDao().clear() // cascades to hr_samples, laps, route_points
            db.dailyAggregateDao().clear()
            db.healthSampleDao().clear()
            db.sleepSessionDao().clear()
            db.syncStateDao().clear()

            db.dailyAggregateDao().upsert(payload.dailyAggregates)
            db.exerciseSessionDao().upsert(payload.exerciseSessions)
            db.exerciseHrSampleDao().insertAll(payload.exerciseHrSamples)
            db.exerciseLapDao().insertAll(payload.exerciseLaps)
            db.exerciseRoutePointDao().insertAll(payload.exerciseRoutePoints)
            db.healthSampleDao().upsert(payload.healthSamples)
            db.sleepSessionDao().upsert(payload.sleepSessions)
            payload.syncState.forEach { db.syncStateDao().upsert(it) }
            payload.goals.forEach { db.goalDao().upsert(it) }
        }

        val totalRecords = payload.dailyAggregates.size + payload.exerciseSessions.size +
            payload.exerciseHrSamples.size + payload.exerciseLaps.size +
            payload.exerciseRoutePoints.size + payload.healthSamples.size +
            payload.sleepSessions.size + payload.syncState.size + payload.goals.size
        Log.d(TAG, "Restore complete: $totalRecords records")
        totalRecords
    }

    suspend fun isDatabaseEmpty(): Boolean {
        val steps = db.dailyAggregateDao().stepDayCount()
        val exercises = db.exerciseSessionDao().totalCount()
        val sleeps = db.sleepSessionDao().totalCount()
        return steps == 0 && exercises == 0 && sleeps == 0
    }

    // --- Drive API helpers ---

    private suspend fun createFile(content: ByteArray, token: String): BackupMetadata {
        val metadata = """{"name":"$BACKUP_FILE_NAME","parents":["appDataFolder"]}"""
        val response = uploadMultipartRelated(
            url = "$DRIVE_API_BASE/upload/drive/v3/files?uploadType=multipart&fields=id,modifiedTime,size",
            metadata = metadata,
            content = content,
            token = token,
        )
        Log.d(TAG, "Created backup file: ${response.id}")
        return BackupMetadata(
            fileId = response.id,
            modifiedTime = response.modifiedTime ?: "",
            sizeBytes = response.size?.toLongOrNull() ?: content.size.toLong(),
        )
    }

    private suspend fun updateFile(fileId: String, content: ByteArray, token: String): BackupMetadata {
        val response = uploadMultipartRelated(
            url = "$DRIVE_API_BASE/upload/drive/v3/files/$fileId?uploadType=multipart&fields=id,modifiedTime,size",
            metadata = "{}",
            content = content,
            token = token,
            isPatch = true,
        )
        Log.d(TAG, "Updated backup file: ${response.id}")
        return BackupMetadata(
            fileId = response.id,
            modifiedTime = response.modifiedTime ?: "",
            sizeBytes = response.size?.toLongOrNull() ?: content.size.toLong(),
        )
    }

    private suspend fun uploadMultipartRelated(
        url: String,
        metadata: String,
        content: ByteArray,
        token: String,
        isPatch: Boolean = false,
    ): DriveFileResponse {
        val boundary = "pulse_backup_boundary_${System.currentTimeMillis()}"
        val body = ByteArrayOutputStream().apply {
            write("--$boundary\r\n".toByteArray())
            write("Content-Type: application/json; charset=UTF-8\r\n\r\n".toByteArray())
            write(metadata.toByteArray(Charsets.UTF_8))
            write("\r\n--$boundary\r\n".toByteArray())
            write("Content-Type: application/gzip\r\n\r\n".toByteArray())
            write(content)
            write("\r\n--$boundary--\r\n".toByteArray())
        }.toByteArray()

        val response = if (isPatch) {
            httpClient.patch(url) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
        } else {
            httpClient.post(url) {
                header("Authorization", "Bearer $token")
                contentType(ContentType.parse("multipart/related; boundary=$boundary"))
                setBody(body)
            }
        }
        return response.body()
    }

    // --- Drive API DTOs ---

    @Serializable
    private data class DriveFileListResponse(
        val files: List<DriveFileResponse> = emptyList(),
    )

    @Serializable
    data class DriveFileResponse(
        val id: String,
        val name: String? = null,
        val modifiedTime: String? = null,
        val size: String? = null,
    )
}
