/*
 *  Copyright 2020 Teja Konjeti
 *   
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.teja.retrofit2.adapter.railway.coroutines

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
 * Adding this class to [Retrofit] allows you to return [NetworkResponse] with suspend functions
 * or functions returning a Call<[NetworkResponse]> from service methods.
 *
 * Note: This adapter must be registered before an adapter that is capable of adapting RxJava
 * streams.
 */
class RailwayCoRoutinesAdapterFactory(private val convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean = true) : CallAdapter.Factory() {
    override fun get(
        callReturnType: Type,
        annotations: Array<Annotation>,
        retrofit: Retrofit
    ): CallAdapter<*, *>? {
        // Keyword suspend will internally convert the function into a Call<returnType>
        val callRawType = getRawType(callReturnType)
        if (callRawType !== Call::class.java || callReturnType !is ParameterizedType) {
            return null
        }

        val returnType = getParameterUpperBound(0, callReturnType)

        val rawType = getRawType(returnType)
        if (rawType !== NetworkResponse::class.java) {
            return null
        } else if (returnType !is ParameterizedType) {
            throw IllegalStateException(
                "NetworkResponse must be parameterized as NetworkResponse<SuccessBody, ErrorBody>"
            )
        }

        val successBodyType = getParameterUpperBound(0, returnType)
        val errorBodyType = getParameterUpperBound(1, returnType)

        val delegateType = Types.newParameterizedTypeWithOwner(
            null,
            Call::class.java,
            successBodyType
        )
        val delegateAdapter = retrofit.nextCallAdapter(
            this,
            delegateType,
            annotations
        )

        val errorBodyConverter = retrofit.nextResponseBodyConverter<Any>(
            null,
            errorBodyType,
            annotations
        )

        @Suppress("UNCHECKED_CAST") // Type of delegateAdapter is not known at compile time.
        return NetworkResponseCallAdapter(
            successBodyType,
            delegateAdapter as CallAdapter<Any, Call<Any>>,
            errorBodyConverter,
            convertToErrorBodyWhenSuccessfulAndCannotParse
        )
    }
}
