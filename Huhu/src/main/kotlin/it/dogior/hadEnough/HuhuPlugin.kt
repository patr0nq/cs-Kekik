package it.dogior.hadEnough

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.utils.AppUtils.parseJson

@CloudstreamPlugin
class HuhuPlugin : Plugin() {
    
    private var countries = mapOf(
        "Turkey" to true // Sadece Türkiye aktif
    )

    
    private val countriesToLang = mapOf(
        "Turkey" to "tr" 
    )

    override fun load(context: Context) {
       
        val sharedPref = context.getSharedPreferences("Huhu", Context.MODE_PRIVATE)

       
        val domain = sharedPref.getString("domain", "huhu.to") ?: "huhu.to"

       
        val lang = "tr"

        
        registerMainAPI(Huhu(domain, countries, lang))

        
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = Settings(this, sharedPref, countries)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
