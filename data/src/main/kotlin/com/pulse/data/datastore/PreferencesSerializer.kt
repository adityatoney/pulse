package com.pulse.data.datastore

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import com.pulse.data.proto.Preferences
import com.google.protobuf.InvalidProtocolBufferException
import java.io.InputStream
import java.io.OutputStream

object PreferencesSerializer : Serializer<Preferences> {
    override val defaultValue: Preferences = Preferences.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): Preferences = try {
        Preferences.parseFrom(input)
    } catch (e: InvalidProtocolBufferException) {
        throw CorruptionException("Unable to read Preferences", e)
    }

    override suspend fun writeTo(t: Preferences, output: OutputStream) = t.writeTo(output)
}
