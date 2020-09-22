/*
 *  Copyright 2020 Teja Konjeti
 *
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */

package xyz.teja.retrofit2.adapter.railway.coroutines

import io.reactivex.BackpressureStrategy
import io.reactivex.Observable
import okhttp3.ResponseBody
import retrofit2.Call
import retrofit2.CallAdapter
import java.lang.reflect.Type

internal class RxJava2RailwayAdapter<T : Any>(
    private val networkResponseAdapter: CallAdapter<T, Call<T>>,
    private val delegateAdapter: CallAdapter<T, Observable<T>>,
    private val isFlowable: Boolean,
    private val isSingle: Boolean,
    private val isMaybe: Boolean
) : CallAdapter<T, Any> {

    override fun adapt(call: Call<T>): Any =
        delegateAdapter.adapt(networkResponseAdapter.adapt(call))
            .run {
                when {
                    isFlowable -> this.toFlowable(BackpressureStrategy.LATEST)
                    isSingle -> this.singleOrError()
                    isMaybe -> this.singleElement()
                    else -> this
                }
            }

    override fun responseType(): Type = ResponseBody::class.java
}