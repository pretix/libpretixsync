package eu.pretix.libpretixsync.api

import okhttp3.Interceptor
import okhttp3.Response

class RateLimitInterceptor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        var response: Response = chain.proceed(chain.request())

        // 429 is how the api indicates a rate limit error
        if (!response.isSuccessful && response.code == 429) {
            val retry_after = Integer.parseInt(response.header("Retry-After", "1")).toLong()
            try {
                Thread.sleep(retry_after * 1000L)
            } catch (e: InterruptedException) {
            }
            response = chain.proceed(chain.request())
        }
        return response
    }
}
