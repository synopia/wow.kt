package wow

import com.github.salomonbrys.kotson.fromJson
import com.google.gson.Gson
import org.apache.http.client.methods.HttpGet
import org.apache.http.client.utils.URIBuilder
import org.apache.http.impl.client.HttpClients
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.util.stream.Collectors

/**
 * Created by synopia on 19/07/16.
 */

val REGIONS = mapOf(
        Pair("US", "https://us.api.battle.net/wow"),
        Pair("EU", "https://eu.api.battle.net/wow"),
        Pair("KR", "https://kr.api.battle.net/wow"),
        Pair("TW", "https://tw.api.battle.net/wow")
)

data class WowItem(val name: String)

interface RestReader {
    fun resource(api: WowApi, url: String): String
}

class HttpReader : RestReader {
    override fun resource(api: WowApi, url: String): String {
        val url = api.baseUrl + url
        val request = HttpGet(URIBuilder(url).setParameter("apikey", api.apiKey).setParameter("locale", api.locale).build())
        val client = HttpClients.createDefault()
        val response = client.execute(request)
        return BufferedReader(InputStreamReader(response.entity.content)).lines().collect(Collectors.joining())
    }
}

class CachedReader(val reader: RestReader) : RestReader {
    override fun resource(api: WowApi, url: String): String {
        val file = File("cache/$url.json")
        if (file.exists()) {
            return file.readLines().joinToString()
        } else {
            File("cache/item").mkdirs()
            val content = reader.resource(api, url)
            file.writeText(content)
            return content
        }
    }
}

class WowApi(val apiKey: String, val region: String = "EU", val locale: String = "en_US") {
    val baseUrl = REGIONS[region]!!
    val gson = Gson()
    val reader = CachedReader(HttpReader())

    fun getResource(resourceUrl: String): String {
        return reader.resource(this, resourceUrl)
    }

    fun item(id: Long): WowItem {
        return gson.fromJson(getResource("/item/$id"))
    }
}
