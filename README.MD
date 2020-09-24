# Retrofit Railway Adapter (Co-Routines & RxJava 2)
![build success](https://img.shields.io/badge/build-success-brightgreen.svg) ![Maven-Central](https://img.shields.io/badge/maven%20central-1.0.0-brightgreen.svg) ![code coverage](https://img.shields.io/badge/code%20coverage-85%25-brightgreen.svg)

An Idiomatic way to parse different success and failure responses for implementing Railway Pattern on Retrofit 2
## Usage
```kotlin
// Can be any class
data class SuccessBody(val success: String)
// Can be any class
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
NetworkResponse.Success(val body: SuccessBody)
// A request that resulted in a response of type  (ErrorBody in this example).
NetworkResponse.ServerError(val body: ErrorBody?, val response: okhttp3.Response, val rawBody: String)
// A request that didn't result in a response or some protocol error.
NetworkResponse.NetworkError(val error: Throwable)
```

> The complete use cases and parsing behaviour can be found [here][Test-Suite]

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
This library supports Co-Routines and also try to convert response body to error body when the response is successful among many other use cases handled.
You can find all of these use cases in the test suite of the coroutines module.

[Test-Suite]: https://github.com/Teja-Konjeti/Retrofit-Railway-Adapter/blob/master/coroutines/src/test/java/xyz/teja/retrofit2/adapter/railway/coroutines/CoRoutinesRailwayAdapterTests.kt
[Jackson]: https://github.com/FasterXML/jackson-module-kotlin
[Kotlinx.serialization]: https://github.com/JakeWharton/retrofit2-kotlinx-serialization-converter/issues
[naturalwarren]: https://gist.github.com/naturalwarren/
[idea]: https://gist.github.com/naturalwarren/56b54759b0f690622938caa91f037be6
