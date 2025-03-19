package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

@CloudstreamPlugin
class HuhuPlugin : Plugin() {
    // Sadece Türkiye'yi içeren sabit bir harita
    private var countries = mapOf(
        "Turkey" to true // Sadece Türkiye aktif
    )

    // Sadece Türkiye'nin dil kodunu içeren harita
    private val countriesToLang = mapOf(
        "Turkey" to "tr" // Türkiye'nin dil kodu
    )

    override fun load(context: Context) {
        // Preferences
        val sharedPref = context.getSharedPreferences("Huhu", Context.MODE_PRIVATE)

        // Domain'i al (varsayılan olarak "huhu.to")
        val domain = sharedPref.getString("domain", "huhu.to") ?: "huhu.to"

        // Dil kodunu sabit olarak "tr" (Türkçe) yap
        val lang = "tr"

        // Plugin'i kaydet
        registerMainAPI(Huhu(domain, countries, lang))

        // Ayarları etkinleştir
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = Settings(this, sharedPref, countries)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
