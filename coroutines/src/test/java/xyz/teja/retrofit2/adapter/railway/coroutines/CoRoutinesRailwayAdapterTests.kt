/*
 *  Copyright 2020 Teja Konjeti
 *
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.teja.retrofit2.adapter.railway.coroutines

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.fail
import retrofit2.Call
import retrofit2.HttpException
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.mock.MockRetrofit
import retrofit2.mock.NetworkBehavior
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import java.net.ProtocolException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@Serializable
data class SuccessBody(val success: String)

@Serializable
data class ErrorBody(val error: String)

interface Service {
    @GET("/")
    suspend fun getBody(): NetworkResponse<SuccessBody, ErrorBody>

    @GET("/")
    fun nothing1(): Call<String>

    @GET("/")
    suspend fun nothing2()

    @GET("/")
    fun nothing3(): Any
}

@ExperimentalSerializationApi
class CoRoutinesRailwayAdapterTests : FreeSpec({
    val server = MockWebServer()
    val retrofit = Retrofit.Builder()
        .baseUrl(server.url("/"))
        .addCallAdapterFactory(RailwayCoRoutinesAdapterFactory())
        .addConverterFactory(Json {
            coerceInputValues = true
            ignoreUnknownKeys = true
        }.asConverterFactory("application/json".toMediaType()))
        .build()
    val testService = retrofit
        .create(Service::class.java)

    "Should parse into success" - {
        "when a success response is received" - {
            server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

            val response = testService.getBody()
            if (response is NetworkResponse.Success) {
                response.body.success shouldBe "test"
            } else {
                print(response)
                fail("Not a success response")
            }
        }
    }

    "Should parse into server error" - {
        "when request is successful but could not parse to success response" - {
            server.enqueue(MockResponse().setBody("{ \"error\": \"test\" }"))

            val response = testService.getBody()
            if (response is NetworkResponse.ServerError) {
                response.body?.error shouldBe "test"
                response.rawBody shouldBe "{ \"error\": \"test\" }"
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "when request is not successful (response code > 100)" - {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("{ \"error\": \"test\" }")
            )

            val response = testService.getBody()
            if (response is NetworkResponse.ServerError) {
                response.body?.error shouldBe "test"
                response.response.code shouldBe 401
                response.rawBody shouldBe "{ \"error\": \"test\" }"
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with null body when unable to parse into either success or error and request is successful" - {
            server.enqueue(MockResponse().setBody("{ \"abc\": \"test\" }"))

            val response = testService.getBody()
            if (response is NetworkResponse.ServerError) {
                response.body shouldBe null
                response.response.code shouldBe 200
                response.rawBody shouldBe "{ \"abc\": \"test\" }"
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with null body when request is not successful and error body cannot be parsed" - {
            server.enqueue(
                MockResponse()
                    .setResponseCode(401)
                    .setBody("{ \"abc\": \"test\" }")
            )

            val response = testService.getBody()
            if (response is NetworkResponse.ServerError) {
                response.body?.error shouldBe null
                response.response.code shouldBe 401
                response.rawBody shouldBe "{ \"abc\": \"test\" }"
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with null body when request is not successful and empty body" - {
            server.enqueue(MockResponse().setResponseCode(401))

            val response = testService.getBody()
            if (response is NetworkResponse.ServerError) {
                response.body?.error shouldBe null
                response.response.code shouldBe 401
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with an error body when an HttpException is thrown" - {
            val exception = HttpException(
                Response.error<String>(
                    401,
                    "{ \"error\": \"test\" }".toResponseBody()
                )
            )

            val networkBehavior = NetworkBehavior.create()

            networkBehavior.setFailureException(exception)
            networkBehavior.setDelay(0, TimeUnit.SECONDS)
            networkBehavior.setFailurePercent(100)

            val service = MockRetrofit
                .Builder(retrofit)
                .networkBehavior(networkBehavior)
                .build()
                .create(Service::class.java)

            val response = service.returningResponse(null).getBody()

            if (response is NetworkResponse.ServerError) {
                response.body?.error shouldBe "test"
                response.response.code shouldBe 401
                response.rawBody shouldBe "{ \"error\": \"test\" }"
            } else {
                print(response)
                fail("Not a error response")
            }
        }
    }

    "Should parse into network error when request is not successful" - {
        "with response code < 100" - {
            server.enqueue(
                MockResponse()
                    .setResponseCode(0)
                    .setBody("{ \"abc\": \"test\" }")
            )

            val response = testService.getBody()
            if (response is NetworkResponse.NetworkError) {
                assert(response.error is ProtocolException)
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with response code > 599" - {
            server.enqueue(
                MockResponse()
                    .setResponseCode(600)
                    .setBody("{ \"abc\": \"test\" }")
            )

            val response = testService.getBody()
            if (response is NetworkResponse.NetworkError) {
                assert(response.error is ProtocolException)
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with response code > 599 and an empty response" - {
            server.enqueue(MockResponse().setResponseCode(600))

            val response = testService.getBody()
            if (response is NetworkResponse.NetworkError) {
                assert(response.error is ProtocolException)
            } else {
                print(response)
                fail("Not a error response")
            }
        }

        "with a socket timeout exception" - {
            val exception = SocketTimeoutException()
            val networkBehavior = NetworkBehavior.create()

            networkBehavior.setFailureException(exception)
            networkBehavior.setDelay(0, TimeUnit.SECONDS)
            networkBehavior.setFailurePercent(100)

            val service = MockRetrofit
                .Builder(retrofit)
                .networkBehavior(networkBehavior)
                .build()
                .create(Service::class.java)

            val response = service.returningResponse(null).getBody()

            if (response is NetworkResponse.NetworkError) {
                response.error shouldBe exception
            } else {
                print(response)
                fail("Not a error response")
            }
        }
    }

    "Should not break existing functionality" - {
        "with \"Call\" return type" - {
            server.enqueue(MockResponse().setBody("\"\""))
            testService.nothing1().execute().body() shouldBe ""
        }

        "with no return type" - {
            server.enqueue(MockResponse())
            testService.nothing2()
        }

        "with non \"Call\" return type" - {
            try {
                testService.nothing3()
                fail("nothing2 should always throw")
            } catch (_: Exception) {
            }
        }

        "with a non IOException" - {
            val exception = NumberFormatException()
            val networkBehavior = NetworkBehavior.create()

            networkBehavior.setFailureException(exception)
            networkBehavior.setDelay(0, TimeUnit.SECONDS)
            networkBehavior.setFailurePercent(100)

            val service = MockRetrofit
                .Builder(retrofit)
                .networkBehavior(networkBehavior)
                .build()
                .create(Service::class.java)

            try {
                service.returningResponse(null).getBody()
                fail("getBody should always throw")
            } catch (_: Exception) {
            }
        }
    }
})
