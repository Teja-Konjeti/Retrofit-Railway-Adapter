/*
 *  Copyright 2020 Teja Konjeti
 *
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.teja.retrofit2.adapter.railway.coroutines

import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import io.kotest.core.spec.style.FreeSpec
import io.kotest.matchers.shouldBe
import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import io.reactivex.observers.TestObserver
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.jupiter.api.fail
import retrofit2.Retrofit
import retrofit2.adapter.rxjava2.RxJava2CallAdapterFactory
import retrofit2.converter.jackson.JacksonConverterFactory
import retrofit2.http.GET
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import java.util.concurrent.TimeUnit

data class SuccessBody(val success: String)

data class ErrorBody(val error: String)

interface Service {
    @GET("/")
    fun getBodyObservable(): Observable<NetworkResponse<SuccessBody, ErrorBody>>

    @GET("/")
    fun getBodyFlowable(): Flowable<NetworkResponse<SuccessBody, ErrorBody>>

    @GET("/")
    fun getBodySingle(): Single<NetworkResponse<SuccessBody, ErrorBody>>

    @GET("/")
    fun getBodyMaybe(): Maybe<NetworkResponse<SuccessBody, ErrorBody>>
}

class RxJava2RailwayAdapterTests : FreeSpec({
    val server = MockWebServer()
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
    val testService = retrofit
        .create(Service::class.java)

    "Should work" - {
        "with Observable" - {
            server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

            val observer = TestObserver<NetworkResponse<SuccessBody, ErrorBody>>()
            testService.getBodyObservable().subscribe(observer)

            observer.awaitDone(5, TimeUnit.SECONDS)

            val response = observer.values()[0]

            if (response is NetworkResponse.Success) {
                response.body.success shouldBe "test"
            } else {
                print(response)
                fail("Not a success response")
            }
        }

        "with Flowable" - {
            server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

            val observer = testService.getBodyFlowable().test()

            observer.awaitDone(5, TimeUnit.SECONDS)

            val response = observer.values()[0]

            if (response is NetworkResponse.Success) {
                response.body.success shouldBe "test"
            } else {
                print(response)
                fail("Not a success response")
            }
        }

        "with Single" - {
            server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

            val observer = TestObserver<NetworkResponse<SuccessBody, ErrorBody>>()
            testService.getBodySingle().subscribe(observer)

            observer.awaitDone(5, TimeUnit.SECONDS)

            val response = observer.values()[0]

            if (response is NetworkResponse.Success) {
                response.body.success shouldBe "test"
            } else {
                print(response)
                fail("Not a success response")
            }
        }

        "with Maybe" - {
            server.enqueue(MockResponse().setBody("{ \"success\": \"test\" }"))

            val observer = TestObserver<NetworkResponse<SuccessBody, ErrorBody>>()
            testService.getBodyMaybe().subscribe(observer)

            observer.awaitDone(5, TimeUnit.SECONDS)

            val response = observer.values()[0]

            if (response is NetworkResponse.Success) {
                response.body.success shouldBe "test"
            } else {
                print(response)
                fail("Not a success response")
            }
        }

    }
})
