package soko.ekibun.bangumi.util

import okhttp3.*
import soko.ekibun.bangumi.api.bangumi.Bangumi
import java.net.URI

object HttpUtil {

    fun get(url: String, header: Map<String, String> = HashMap(), callback: Callback) {
        OkHttpClient().newCall(Request.Builder()
                .url(url)
                .headers(Headers.of(header))
                .build())
                .enqueue(callback)
    }

    fun getCall(url: String, header: Map<String, String> = HashMap(), body: RequestBody? = null, useCookie: Boolean = true): Call {
        val request = Request.Builder()
                .url(url)
                .headers(Headers.of(header))
        if (body != null) request.post(body)
        val httpClient = OkHttpClient.Builder()
        if(useCookie) httpClient.cookieJar(WebViewCookieHandler())
        return httpClient.build().newCall(request.build())
    }

    fun getUrl(url: String, baseUri: URI?): String{
        if(url in arrayOf("/img/info_only.png", "/img/info_only_m.png", "/img/no_icon_subject.png")
                && baseUri?.toASCIIString()?.startsWith(Bangumi.SERVER) == true) return ""
        return try{
            baseUri?.resolve(url)?.toASCIIString() ?: URI.create(url).toASCIIString()
        }catch (e: Exception){ url }
    }
}