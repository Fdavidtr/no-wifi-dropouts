package io.multinet.mobility.data.preferences

import androidx.datastore.core.CorruptionException
import androidx.datastore.core.Serializer
import io.multinet.mobility.datastore.MobilitySettings
import java.io.InputStream
import java.io.OutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

object UserPreferencesSerializer : Serializer<MobilitySettings> {
    override val defaultValue: MobilitySettings = MobilitySettings.getDefaultInstance()

    override suspend fun readFrom(input: InputStream): MobilitySettings = withContext(Dispatchers.IO) {
        try {
            MobilitySettings.parseFrom(input)
        } catch (exception: Exception) {
            throw CorruptionException("Cannot read mobility settings proto.", exception)
        }
    }

    override suspend fun writeTo(t: MobilitySettings, output: OutputStream) = withContext(Dispatchers.IO) {
        t.writeTo(output)
    }
}

