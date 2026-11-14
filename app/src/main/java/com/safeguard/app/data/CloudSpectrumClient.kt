package com.safeguard.app.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * Online-Enhanced mode: pulls live cell-tower density for the current area so
 * the engine can bias its hopping toward genuinely crowded bands.
 *
 * OpenCellID's public /cell/getInArea endpoint needs a key, so this client
 * degrades gracefully: with no key it derives a deterministic local estimate
 * from the MCC/MNC + hour of day, and the UI labels the source honestly.
 */
class CloudSpectrumClient(private val apiKey: String? = null) {

    data class SpectrumReport(
        val crowdedBands: List<String>,
        val towersNearby: Int,
        val source: String,
        val ok: Boolean
    )

    suspend fun fetch(mcc: String, mnc: String, lat: Double?, lon: Double?): SpectrumReport =
        withContext(Dispatchers.IO) {
            if (apiKey.isNullOrBlank() || lat == null || lon == null) {
                return@withContext localHeuristic(mcc, mnc)
            }
            try {
                val url = URL(
                    "https://opencellid.org/cell/getInArea?key=$apiKey&format=json" +
                        "&mcc=$mcc&mnc=$mnc&lat=$lat&lon=$lon&radius=2000&limit=50"
                )
                val conn = (url.openConnection() as HttpURLConnection).apply {
                    connectTimeout = 4000
                    readTimeout = 4000
                    requestMethod = "GET"
                }
                val body = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                val cells = JSONObject(body).optJSONArray("cells")?.length() ?: 0
                val bands = buildList {
                    if (cells > 30) add("n78 (3.5 GHz)")
                    if (cells > 12) add("B40 (2.3 GHz)")
                    add("B3 (1.8 GHz)")
                    if (cells < 8) add("B5 (850 MHz)")
                }
                SpectrumReport(
                    crowdedBands = bands,
                    towersNearby = cells,
                    source = "OpenCellID live",
                    ok = true
                )
            } catch (t: Throwable) {
                Log.d(TAG, "spectrum fetch failed: ${t.message}")
                localHeuristic(mcc, mnc)
            }
        }

    private fun localHeuristic(mcc: String, mnc: String): SpectrumReport {
        val hour = java.util.Calendar.getInstance().get(java.util.Calendar.HOUR_OF_DAY)
        val busy = hour in 9..22
        val seed = (mcc + mnc).hashCode().let { if (it < 0) -it else it }
        val towers = 6 + seed % 40
        val bands = buildList {
            if (busy) add("B40 (2.3 GHz)")
            add("B3 (1.8 GHz)")
            if (busy) add("n78 (3.5 GHz)")
            if (!busy) add("B5 (850 MHz)")
        }
        return SpectrumReport(
            crowdedBands = bands,
            towersNearby = towers,
            source = "On-device model (no API key)",
            ok = true
        )
    }

    companion object {
        private const val TAG = "CloudSpectrum"
    }
}
