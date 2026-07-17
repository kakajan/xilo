package ir.xilo.app.data.repository

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import ir.xilo.app.data.remote.api.XiloApiService
import ir.xilo.app.data.remote.dto.BrandSettingsDto
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

data class PlatformBrand(
    val nameFa: String = "آیله",
    val nameEn: String = "aile",
    val display: String = "آیله | aile",
)

val DefaultPlatformBrand = PlatformBrand()

@Singleton
class BrandRepository @Inject constructor(
    @ApplicationContext context: Context,
    private val apiService: XiloApiService,
    private val json: Json,
) {
    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _brand = MutableStateFlow(loadCachedOrDefault())
    val brand: StateFlow<PlatformBrand> = _brand.asStateFlow()

    suspend fun syncBrand(): Result<PlatformBrand> {
        return try {
            val response = apiService.getPlatformSettings()
            val dto = response.brand
            val next = if (dto != null) {
                cacheBrand(dto)
                dto.toPlatformBrand()
            } else {
                DefaultPlatformBrand
            }
            _brand.value = next
            Result.success(next)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun loadCachedOrDefault(): PlatformBrand {
        val raw = prefs.getString(KEY_BRAND_JSON, null) ?: return DefaultPlatformBrand
        return try {
            json.decodeFromString<BrandSettingsDto>(raw).toPlatformBrand()
        } catch (_: Exception) {
            DefaultPlatformBrand
        }
    }

    private fun cacheBrand(dto: BrandSettingsDto) {
        prefs.edit().putString(KEY_BRAND_JSON, json.encodeToString(dto)).apply()
    }

    private fun BrandSettingsDto.toPlatformBrand(): PlatformBrand {
        val fa = nameFa.trim().ifBlank { DefaultPlatformBrand.nameFa }
        val en = nameEn.trim().ifBlank { DefaultPlatformBrand.nameEn }
        val shown = this.display.trim().ifBlank { "$fa | $en" }
        return PlatformBrand(nameFa = fa, nameEn = en, display = shown)
    }

    companion object {
        private const val PREFS_NAME = "xilo_brand"
        private const val KEY_BRAND_JSON = "platform_brand_json"
    }
}
