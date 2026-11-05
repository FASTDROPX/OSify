package dev.osify.app.data

import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsStore by preferencesDataStore("settings")
data class AppPreferences(
    val onboarded: Boolean=false, val language: String="system", val theme: String="system", val dynamicColor: Boolean=false,
    val motion: String="full", val haptics: Boolean=true, val wifiOnly: Boolean=true, val meteredWarning: Boolean=true,
    val concurrent: Int=1, val externalEngineConsent: Boolean=false, val profile: String="BALANCED", val amoled: Boolean=false, val catalogSort: String="curated", val favorites: Set<String> = emptySet()
)
class Preferences(private val context: Context) {
    val flow=context.settingsStore.data.catch { e -> if(e is IOException) emit(emptyPreferences()) else throw e }.map { p ->
        AppPreferences(p[booleanPreferencesKey("onboarded")]?:false,p[stringPreferencesKey("language")]?:"system",p[stringPreferencesKey("theme")]?:"system",
            p[booleanPreferencesKey("dynamic_brand_v3")]?:false,p[stringPreferencesKey("motion")]?:"full",p[booleanPreferencesKey("haptics")]?:true,
            p[booleanPreferencesKey("wifi")]?:true,p[booleanPreferencesKey("metered")]?:true,(p[intPreferencesKey("concurrent")]?:1).coerceIn(1,3),
            p[booleanPreferencesKey("termux")]?:false,p[stringPreferencesKey("profile")]?:"BALANCED",p[booleanPreferencesKey("amoled")]?:false,p[stringPreferencesKey("catalog_sort")]?:"curated",p[stringSetPreferencesKey("catalog_favorites")]?:emptySet())
    }
    suspend fun boolean(key: String,value: Boolean) { require(key in setOf("onboarded","dynamic","haptics","wifi","metered","termux","amoled")); context.settingsStore.edit { it[booleanPreferencesKey(if(key=="dynamic") "dynamic_brand_v3" else key)]=value } }
    suspend fun string(key: String,value: String) {
        require(value in when(key) { "language"->setOf("system","en","ru"); "theme"->setOf("system","light","dark"); "motion"->setOf("full","reduced","off"); "profile"->setOf("SPEED","BALANCED","COMFORT"); "catalog_sort"->setOf("curated","name_asc","name_desc","size_asc","size_desc"); else->emptySet() })
        context.settingsStore.edit { it[stringPreferencesKey(key)]=value }
    }
    suspend fun favorite(key: String) {require(key.length in 1..160);context.settingsStore.edit {p->val old=p[stringSetPreferencesKey("catalog_favorites")]?:emptySet();p[stringSetPreferencesKey("catalog_favorites")]=if(key in old) old-key else old+key}}
    suspend fun concurrency(value: Int) { require(value in 1..3); context.settingsStore.edit { it[intPreferencesKey("concurrent")]=value } }
    suspend fun reset() { context.settingsStore.edit { p -> val onboarded=p[booleanPreferencesKey("onboarded")]?:false; p.clear(); p[booleanPreferencesKey("onboarded")]=onboarded } }
}
