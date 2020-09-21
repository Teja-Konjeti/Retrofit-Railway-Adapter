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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.fail
import retrofit2.Retrofit
import retrofit2.http.GET
import retrofit2.mock.MockRetrofit
import retrofit2.mock.NetworkBehavior
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

@Serializable
data class SuccessBody(val success: String)

@Serializable
data class ErrorBody(val error: String)

interface Service {
    @GET("/")
    suspend fun getBody(): NetworkResponse<SuccessBody, ErrorBody>
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

    "Should parse into success body" - {
        server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

        val response = testService.getBody()
        if (response is NetworkResponse.Success) {
            response.body?.success shouldBe "test"
        } else {
            print(response)
            fail("Not a success response")
        }
    }

    "Should parse into error body even when request is successful and no success response is received" - {
        server.enqueue(MockResponse().setBody("{ \"error\": \"test\" }"))

        val response = testService.getBody()
        if (response is NetworkResponse.ServerError) {
            response.body?.error shouldBe "test"
        } else {
            print(response)
            fail("Not a error response")
        }
    }

    "Should parse into success with null body when unable to parse into either success or error and request is successful" - {
        server.enqueue(MockResponse().setBody("{ \"abc\": \"test\" }"))

        val response = testService.getBody()
        if (response is NetworkResponse.Success) {
            response.body shouldBe null
        } else {
            print(response)
            fail("Not a error response")
        }
    }

    "Should parse into error body when request is not successful" - {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{ \"error\": \"test\" }")
        )

        val response = testService.getBody()
        if (response is NetworkResponse.ServerError) {
            response.body?.error shouldBe "test"
            response.code shouldBe 401
        } else {
            print(response)
            fail("Not a error response")
        }
    }

    "Should parse into error with null body when request is not successful" - {
        server.enqueue(
            MockResponse()
                .setResponseCode(401)
                .setBody("{ \"abc\": \"test\" }")
        )

        val response = testService.getBody()
        if (response is NetworkResponse.ServerError) {
            response.body?.error shouldBe null
            response.code shouldBe 401
        } else {
            print(response)
            fail("Not a error response")
        }
    }

    "Should parse into network error when request is not successful" - {
        val networkBehavior = NetworkBehavior.create()

        networkBehavior.setFailureException(SocketTimeoutException())
        networkBehavior.setDelay(0, TimeUnit.SECONDS)
        networkBehavior.setFailurePercent(100)

        val service = MockRetrofit
            .Builder(retrofit)
            .networkBehavior(networkBehavior)
            .build()
            .create(Service::class.java)

        val response = service.returningResponse(null).getBody()

        if (response is NetworkResponse.NetworkError) {
            assert(response.error is IOException)
        } else {
            print(response)
            fail("Not a error response")
        }
    }
})
