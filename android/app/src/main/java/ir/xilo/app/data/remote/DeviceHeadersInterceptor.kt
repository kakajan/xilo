package ir.xilo.app.data.remote

import android.os.Build
import okhttp3.Interceptor
import okhttp3.Response

/** Attaches device identity headers for auth session metadata. */
class DeviceHeadersInterceptor(
    private val deviceName: String = Build.MODEL.orEmpty().ifBlank { "Android" },
    private val platform: String = "android",
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request().newBuilder()
            .header("X-Device-Name", deviceName.take(100))
            .header("X-Device-Platform", platform)
            .build()
        return chain.proceed(request)
    }
}
