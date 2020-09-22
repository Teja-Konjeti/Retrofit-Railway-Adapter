/*
 *  Copyright 2020 Teja Konjeti
 *
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.teja.retrofit2.adapter.railway.coroutines

import io.reactivex.Flowable
import io.reactivex.Maybe
import io.reactivex.Observable
import io.reactivex.Single
import retrofit2.Call
import retrofit2.CallAdapter
import retrofit2.Retrofit
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import xyz.teja.retrofit2.adapter.railway.Types
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type

/**
 * A [CallAdapter.Factory] which allows [NetworkResponse] objects to be returned from RxJava
 * streams.
 *
 * Adding this class to [Retrofit] allows you to return [Observable], [Flowable], [Single], or
 * [Maybe] types parameterized with [NetworkResponse] from service methods.
 *
 * Note: This adapter must be registered before an adapter that is capable of adapting RxJava
 * streams.
 */
object RxJava2RailwayAdapterFactory : CallAdapter.Factory() {
    override fun get(
        returnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        val rawType = getRawType(returnType)

        val isFlowable = rawType === Flowable::class.java
        val isSingle = rawType === Single::class.java
        val isMaybe = rawType === Maybe::class.java
        if (rawType !== Observable::class.java && !isFlowable && !isSingle && !isMaybe) {
            return null
        }

        if (returnType !is ParameterizedType) {
            throw IllegalStateException(
                "${rawType.simpleName} return type must be parameterized as " +
                        "${rawType.simpleName}<Foo> or ${rawType.simpleName}<? extends Foo>"
            )
        }

        val observableEmissionType = getParameterUpperBound(0, returnType)
        if (getRawType(observableEmissionType) != NetworkResponse::class.java) {
            return null
        }

        if (observableEmissionType !is ParameterizedType) {
            throw IllegalStateException(
                "NetworkResponse must be parameterized as NetworkResponse<SuccessBody, ErrorBody>"
            )
        }

        val delegateType = Types.newParameterizedTypeWithOwner(
            null,
            Observable::class.java,
            observableEmissionType
        )
        val observableAdapter = retrofit.nextCallAdapter(
            this,
            delegateType,
            annotations
        )
        // This should return a NetworkResponseCallAdapter
        val networkResponseAdapter = retrofit.nextCallAdapter(
            this,
            Types.newParameterizedTypeWithOwner(
                null,
                Call::class.java,
                observableEmissionType
            ),
            annotations
        )

        @Suppress("UNCHECKED_CAST") // Type of delegateAdapter is not known at compile time.
        return RxJava2RailwayAdapter(
            networkResponseAdapter as CallAdapter<NetworkResponse<*, *>, Call<NetworkResponse<*, *>>>,
            observableAdapter as CallAdapter<NetworkResponse<*, *>, Observable<NetworkResponse<*, *>>>,
            isFlowable,
            isSingle,
            isMaybe
        )
    }
}