package com.pulse.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.pulse.data.proto.FeatureFlags
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object FeatureFlagsSerializer : Serializer<FeatureFlags> {
    override val defaultValue: FeatureFlags = FeatureFlags.newBuilder()
        .setWowMomOnDashboard(true)
        .setForceDarkMode(false)
        .setFaultInjectionActive(false)
        .setFaultInjectionExpiresAtMs(0L)
        .setUseDynamicColor(false)
        .build()

    override suspend fun readFrom(input: InputStream): FeatureFlags = try {
        FeatureFlags.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to read FeatureFlags", e)
    }

    override suspend fun writeTo(t: FeatureFlags, output: OutputStream) {
        t.writeTo(output)
    }
}
