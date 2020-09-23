/*
 *  Copyright 2020 Teja Konjeti
 *   
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.teja.retrofit2.adapter.railway

import okhttp3.Response

/**
 * Represents the result of making a network request.
 *
 * @param T success body type.
 * @param U error body type.
 */
sealed class NetworkResponse<out T : Any, out U : Any> {

    /**
     * A request that resulted in a successful response of type T.
     */
    data class Success<T : Any>(val body: T) : NetworkResponse<T, Nothing>()

    /**
     * A request that resulted in a response of type U.
     * @param body parsed error body
     * @param response raw response received
     * @param rawBody this is provided as the body of the raw response is already consumed
     */
    data class ServerError<U : Any>(val body: U?, val response: Response, val rawBody: String) :
        NetworkResponse<Nothing, U>()

    /**
     * A request that didn't result in a response or some protocol error.
     */
    data class NetworkError(val error: Throwable) : NetworkResponse<Nothing, Nothing>()
}
