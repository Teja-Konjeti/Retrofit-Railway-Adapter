# Retrofit Railway Adapter
![version](https://img.shields.io/badge/version-1.0.0-brightgreen.svg) ![build success](https://img.shields.io/badge/build-success-brightgreen.svg) ![tests passed](https://img.shields.io/badge/tests-passed-brightgreen.svg)

An Idiomatic way to parse different success and failure responses for implementing Railway Pattern on Retrofit 2
## Usage
```kotlin
data class SuccessBody(val success: String)

data class ErrorBody(val error: String)

interface Service {
    @GET("/") fun getBody(): Call<NetworkResponse<SuccessBody, ErrorBody>>
    @GET("/") suspend fun getBody(): NetworkResponse<SuccessBody, ErrorBody>
    @GET("/") fun getBodyObservable(): Observable<NetworkResponse<SuccessBody, ErrorBody>>
}
```

This will parse to one of the following responses
```kotlin
    // A request that resulted in a successful response of type T (SuccessBody in this example).
    data class Success<T : Any>(val body: T) : NetworkResponse<T, Nothing>()
    // A request that resulted in a response of type  (ErrorBody in this example).
    data class ServerError<U : Any>(val body: U?, val response: Response, val rawBody: String) :
        NetworkResponse<Nothing, U>()
    // A request that didn't result in a response or some protocol error.
    data class NetworkError(val error: Throwable) : NetworkResponse<Nothing, Nothing>()
```

> The complete use cases and parsing behaviour can be found in the test suite of the coroutines module.

> Note: DO NOT use Gson as your Retrofit converter. Gson uses reflection and will ALWAYS parse into SuccessBody even when a property is non-nullable which causes run-time exceptions.

> This library has been tested with [Jackson][Jackson] and [Kotlinx.serialization][Kotlinx.serialization] libraries.

# Setup (Co-Routines or Generic Call)
### Gradle
```gradle
dependencies {
    // Appropriate Kotlin-stdlib, Retrofit and OkHttp3 (latest version) need to be implemented with the adpater
    implementation "xyz.teja.retrofit2:coroutines-railway-adapter:1.0.0"
}
```
### Retrofit
```kotlin
    val retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addCallAdapterFactory(CoRoutinesRailwayAdapterFactory)
        .addConverterFactory(
            JacksonConverterFactory.create(
                JsonMapper
                    .builder()
                    .addModules(KotlinModule(nullIsSameAsDefault = false))
                    .build()
            )
        )
        .build()
    val testService = retrofit.create(Service::class.java)
```
# Setup (RxJava)
### Gradle
```gradle
dependencies {
    // Appropriate Kotlin-stdlib, Retrofit and OkHttp3 (latest version) need to be implemented with the adpater
    implementation "xyz.teja.retrofit2:rxjava2-railway-adapter:1.0.0"
}
```
### Retrofit
```kotlin
    val retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addCallAdapterFactory(RxJava2RailwayAdapterFactory)
        .addCallAdapterFactory(RxJava2CallAdapterFactory.create())
        .addCallAdapterFactory(CoRoutinesRailwayAdapterFactory)
        .addConverterFactory(
            JacksonConverterFactory.create(
                JsonMapper
                    .builder()
                    .addModules(KotlinModule(nullIsSameAsDefault = false))
                    .build()
            )
        )
        .build()
    val testService = retrofit.create(Service::class.java)
```

> Kudos to [naturalwarren][naturalwarren] for the [idea][idea]. This implementation is completely different from his implementation.
This library supports Co-Routines and also try to convert reponse body to error body when the response successful among many other use cases handled.
You can find all of these usecases in the test suite of the coroutines module.

> This library also has 85% test coverage from the JaCoCo test coverage report.

[Jackson]: https://github.com/FasterXML/jackson-module-kotlin
[Kotlinx.serialization]: https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues
[naturalwarren]: https://gist.github.com/naturalwarren/
[idea]: https://gist.github.com/naturalwarren/56b54759b0f690622938caa91f037be6
