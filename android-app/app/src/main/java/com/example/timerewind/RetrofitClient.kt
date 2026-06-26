import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import com.example.timerewind.ApiService

object RetrofitClient {

    private val interceptor = HttpLoggingInterceptor().apply {
       level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(interceptor)
        .build()

    private const val BASE_URL = "http://192.168.1.13:5000" // Your PC's IP

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl("http://192.168.1.13:5000/") // Ensures trailing slash
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()

        retrofit.create(ApiService::class.java)
    }
}
