package io.multinet.mobility.domain

enum class WifiBandPreference {
    ANY,
    BAND_2_4_GHZ,
    BAND_5_GHZ,
    BAND_6_GHZ;

    companion object {
        fun fromFrequency(frequencyMhz: Int?): WifiBandPreference = when {
            frequencyMhz == null -> ANY
            frequencyMhz in 2400..2500 -> BAND_2_4_GHZ
            frequencyMhz in 4900..5900 -> BAND_5_GHZ
            frequencyMhz in 5925..7125 -> BAND_6_GHZ
            else -> ANY
        }
    }
}

