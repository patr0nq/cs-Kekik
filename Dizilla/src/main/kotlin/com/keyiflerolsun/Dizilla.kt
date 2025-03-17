// ! Bu araç @keyiflerolsun tarafından | @KekikAkademi için yazılmıştır.

package com.keyiflerolsun

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors

class Dizilla : MainAPI() {
    override var mainUrl              = "https://dizilla.nl"
    override var name                 = "Dizilla"
    override val hasMainPage          = true
    override var lang                 = "tr"
    override val hasQuickSearch       = true
    override val supportedTypes       = setOf(TvType.TvSeries)

    // ! CloudFlare bypass
    override var sequentialMainPage = true
    // override var sequentialMainPageDelay       = 250L
    // override var sequentialMainPageScrollDelay = 250L

    override val mainPage = mainPageOf(
        "${mainUrl}/tum-bolumler"          to "Altyazılı Bölümler",
        "${mainUrl}/dublaj-bolumler"       to "Dublaj Bölümler",
        "${mainUrl}/dizi-turu/aile"        to "Aile",
        "${mainUrl}/dizi-turu/aksiyon"     to "Aksiyon",
        "${mainUrl}/dizi-turu/bilim-kurgu" to "Bilim Kurgu",
        "${mainUrl}/dizi-turu/romantik"    to "Romantik",
        "${mainUrl}/dizi-turu/komedi"      to "Komedi"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = if (request.data.contains("dizi-turu")) { 
            document.select("div.grid-cols-3 a").mapNotNull { it.diziler() }
        } else {
            document.select("div.grid a").mapNotNull { it.sonBolumler() }
        }

        return newHomePageResponse(request.name, home)
    }

    private fun Element.diziler(): SearchResponse? {
        val title     = this.selectFirst("h2")?.text() ?: return null
        val href      = fixUrl(this.attr("href"), mainUrl) // DÜZELTİLDİ
        val posterUrl = fixUrl(this.selectFirst("img")?.attr("data-src") ?: fixUrl(this.selectFirst("img")?.attr("src"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private suspend fun Element.sonBolumler(): SearchResponse? {
        val name   = this.selectFirst("h2")?.text() ?: return null
        val epName = this.selectFirst("div.opacity-80")!!.text().replace(". Sezon ", "x").replace(". Bölüm", "")
        val title  = "$name - $epName"

        val epDoc     = app.get(fixUrl(this.attr("href"), mainUrl).document // DÜZELTİLDİ
        val href      = fixUrl(epDoc.selectFirst("a.relative")?.attr("href"), mainUrl) ?: return null // DÜZELTİLDİ
        val posterUrl = fixUrl(epDoc.selectFirst("img.imgt")?.attr("onerror")?.substringAfter("= '")?.substringBefore("';"))

        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) { this.posterUrl = posterUrl }
    }

    private fun SearchItem.toSearchResponse(): SearchResponse? {
        return newTvSeriesSearchResponse(
            title ?: return null,
            fixUrl("${mainUrl}/${slug}", mainUrl), // DÜZELTİLDİ
            TvType.TvSeries,
        ) {
            this.posterUrl = poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val mainReq  = app.get(mainUrl)
        val mainPage = mainReq.document
        val cKey     = mainPage.selectFirst("input[name='cKey']")?.attr("value") ?: return emptyList()
        val cValue   = mainPage.selectFirst("input[name='cValue']")?.attr("value") ?: return emptyList()

        val veriler   = mutableListOf<SearchResponse>()

        val searchReq = app.post(
            "${mainUrl}/bg/searchcontent",
            data = mapOf(
                "cKey"       to cKey,
                "cValue"     to cValue,
                "searchterm" to query
            ),
            headers = mapOf(
                "Accept"           to "application/json, text/javascript, */*; q=0.01",
                "X-Requested-With" to "XMLHttpRequest"
            ),
            referer = "${mainUrl}/",
            cookies = mapOf(
                "showAllDaFull"   to "true",
                "PHPSESSID"       to mainReq.cookies["PHPSESSID"].toString(),
            )
        ).parsedSafe<SearchResult>()

        if (searchReq?.data?.state != true) {
            throw ErrorLoadingException("Invalid Json response")
        }

        searchReq.data.result?.forEach { searchItem ->
            veriler.add(searchItem.toSearchResponse() ?: return@forEach)
        }

        return veriler
    }

    override suspend fun quickSearch(query: String): List<SearchResponse> = search(query)

    override suspend fun load(url: String): LoadResponse? {
        val document = app.get(url).document

        val title       = document.selectFirst("div.page-top h1")?.text() ?: return null
        val poster      = fixUrl(document.selectFirst("div.page-top img")?.attr("src")) ?: fixUrl(document.selectFirst("div.page-top img")?.attr("data-src"))
        val year        = document.selectXpath("//span[text()='Yayın tarihi']//following-sibling::span").text().trim().split(" ").last().toIntOrNull()
        val description = document.selectFirst("div.mv-det-p")?.text()?.trim() ?: document.selectFirst("div.w-full div.text-base")?.text()?.trim()
        val tags        = document.select("[href*='dizi-turu']").map { it.text() }
        val rating      = document.selectFirst("a[href*='imdb.com'] span")?.text()?.trim().toRatingInt()
        val duration    = Regex("(\\d+)").find(document.select("div.gap-3 span.text-sm")[1].text())?.value?.toIntOrNull()
        val actors      = document.select("[href*='oyuncu']").map {
            Actor(it.text())
        }

        val episodeList = mutableListOf<Episode>()
        document.selectXpath("//div[contains(@class, 'gap-2')]/a[contains(@href, '-sezon')]").forEach {
            val seasonUrl = fixUrl(it.attr("href"), mainUrl) // DÜZELTİLDİ
            val epDoc = app.get(seasonUrl).document
        
            epDoc.select("div.episodes div.cursor-pointer").forEach ep@ { episodeElement ->
                val epName        = episodeElement.select("a").last()?.text()?.trim() ?: return@ep
                val epHref        = fixUrl(episodeElement.selectFirst("a.opacity-60")?.attr("href"), mainUrl) ?: return@ep // DÜZELTİLDİ
                val epDescription = episodeElement.selectFirst("span.t-content")?.text()?.trim()
                val epPoster      = fixUrl(epDoc.selectFirst("img.object-cover")?.attr("src"))
        
                episodeList.add(newEpisode(epHref) {
                    this.name = epName
                    this.description = epDescription
                    this.posterUrl = epPoster
                })
            }
        
            epDoc.select("div.dub-episodes div.cursor-pointer").forEach epDub@ { dubEpisodeElement ->
                val epName        = dubEpisodeElement.select("a").last()?.text()?.trim() ?: return@epDub
                val epHref        = fixUrl(dubEpisodeElement.selectFirst("a.opacity-60")?.attr("href"), mainUrl) ?: return@epDub // DÜZELTİLDİ
                val epDescription = dubEpisodeElement.selectFirst("span.t-content")?.text()?.trim()
                val epPoster      = fixUrl(epDoc.selectFirst("img.object-cover")?.attr("src"))
        
                episodeList.add(newEpisode(epHref) {
                    this.name = "$epName Dublaj"
                    this.description = epDescription
                    this.posterUrl = epPoster
                })
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList) {
            this.posterUrl = poster
            this.year      = year
            this.plot      = description
            this.tags      = tags
            this.rating    = rating
            this.duration  = duration
            addActors(actors)
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val fullUrl = if (data.startsWith("http")) data else fixUrl(data, mainUrl) // DÜZELTİLDİ
        val document = app.get(fullUrl).document
        val iframes  = mutableSetOf<String>()

        val alternatifler = document.select("a[href*='player']")
        if (alternatifler.isEmpty()) {
            val iframe = fixUrl(document.selectFirst("div#playerLsDizilla iframe")?.attr("src"), mainUrl) ?: return false
            
            loadExtractor(iframe, mainUrl, subtitleCallback, callback)
        } else {
            alternatifler.forEach {
                val playerUrl = fixUrl(it.attr("href"), mainUrl) // DÜZELTİLDİ
                val playerDoc = app.get(playerUrl).document
                val iframe    = fixUrl(playerDoc.selectFirst("div#playerLsDizilla iframe")?.attr("src"), mainUrl) ?: return@forEach

                if (iframe in iframes) continue
                iframes.add(iframe)
                
                loadExtractor(iframe, mainUrl, subtitleCallback, callback)
            }
        }

        return true
    }
}