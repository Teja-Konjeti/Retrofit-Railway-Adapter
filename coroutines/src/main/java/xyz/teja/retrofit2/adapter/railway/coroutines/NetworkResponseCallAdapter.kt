/*
 *  Copyright 2020 Teja Konjeti
 *   
 *  This Source Code Form is subject to the terms of the Mozilla
 *  Public License, v. 2.0. If a copy of the MPL was not distributed
 *  with this file, You can obtain one at https://mozilla.org/MPL/2.0/.
 */
package xyz.teja.retrofit2.adapter.railway.coroutines

import okhttp3.Request
import okhttp3.ResponseBody
import okio.Timeout
import retrofit2.*
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import java.io.IOException
import java.lang.reflect.Type
import java.util.*

internal class NetworkResponseCallAdapter<T : Any, U : Any>(
    private val successBodyType: Type,
    private val delegateAdapter: CallAdapter<T, Call<T>>,
    private val errorConverter: Converter<ResponseBody, U>,
    private val convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
) : CallAdapter<T, Call<NetworkResponse<T, U>>> {
    override fun adapt(call: Call<T>): Call<NetworkResponse<T, U>> =
        CallbackCall(
            convertToErrorBodyWhenSuccessfulAndCannotParse,
            delegateAdapter.adapt(call),
            errorConverter,
        )

    override fun responseType(): Type = successBodyType

    internal class CallbackCall<T : Any, U : Any>(
        private val convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
        private val delegate: Call<T>,
        private val errorConverter: Converter<ResponseBody, U>,
    ) : Call<NetworkResponse<T, U>> {
        override fun enqueue(callback: Callback<NetworkResponse<T, U>>) {
            Objects.requireNonNull(callback, "callback == null")
            delegate.enqueue(
                object : Callback<T> {
                    override fun onResponse(call: Call<T>, response: Response<T>) {
                        callback.onResponse(this@CallbackCall, success(response))
                    }

                    override fun onFailure(call: Call<T>, throwable: Throwable) {
                        callback.onResponse(this@CallbackCall, failure(throwable))
                    }
                })
        }

        private fun success(response: Response<T>): Response<NetworkResponse<T, U>> {
            return wrapNetworkResponseInResponse(
                convertResponseToNetworkResponse(
                    response,
                    errorConverter,
                    convertToErrorBodyWhenSuccessfulAndCannotParse
                ),
                response
            )
        }

        private fun failure(throwable: Throwable): Response<NetworkResponse<T, U>> {
            return wrapNetworkResponseInResponse(
                throwableToNetworkResponse(errorConverter, throwable),
                null
            )
        }

        private fun convertResponseToNetworkResponse(
            response: Response<T>,
            errorConverter: Converter<ResponseBody, U>,
            convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
        ): NetworkResponse<T, U> {
            val body = response.body()
            return when {
                body != null -> NetworkResponse.Success(body)
                response.isSuccessful -> {
                    val rawSuccessBody = response.raw().body
                    if (convertToErrorBodyWhenSuccessfulAndCannotParse && rawSuccessBody != null) {
                        try {
                            NetworkResponse.ServerError(
                                errorConverter.convert(rawSuccessBody),
                                response.code()
                            )
                        } catch (e: Exception) {
                            NetworkResponse.Success(null)
                        }
                    } else {
                        NetworkResponse.Success(null)
                    }
                }
                else -> errorBodyToNetworkResponse(response, errorConverter)
            }
        }

        private fun throwableToNetworkResponse(
            errorConverter: Converter<ResponseBody, U>,
            throwable: Throwable,
        ): NetworkResponse<T, U> {
            if (throwable is HttpException) {
                val response = throwable.response()
                if (response != null) {
                    return errorBodyToNetworkResponse(response, errorConverter)
                }
            }

            return NetworkResponse.NetworkError(throwable)
        }

        /// Will try to parse to Error Body and return a Server Error if parsing is successful
        /// If parsing fails, returns a Server Error for codes > 100 and Network Error otherwise
        private fun errorBodyToNetworkResponse(
            response: Response<*>,
            errorConverter: Converter<ResponseBody, U>,
        ): NetworkResponse<T, U> {
            val error = response.errorBody()
            if (error != null && error.contentLength() != 0L) {
                return try {
                    NetworkResponse.ServerError(
                        errorConverter.convert(error),
                        response.code()
                    )
                } catch (e: Exception) {
                    return if (response.code() < 100) {
                        NetworkResponse.NetworkError(
                            IOException("Couldn't deserialize error body: ${error.string()}", e)
                        )
                    } else {
                        NetworkResponse.ServerError(null, response.code())
                    }
                }
            } else {
                return if (response.code() < 100) {
                    NetworkResponse.NetworkError(IOException("Empty Error Body"))
                } else {
                    NetworkResponse.ServerError(null, response.code())
                }
            }
        }

        private fun wrapNetworkResponseInResponse(
            parsedResponse: NetworkResponse<T, U>,
            retrofitResponse: Response<T>?
        ): Response<NetworkResponse<T, U>> =
            if (retrofitResponse?.isSuccessful == true) {
                Response.success(
                    parsedResponse,
                    retrofitResponse.raw()
                )
            } else {
                Response.success(200, parsedResponse)
            }

        override fun isExecuted(): Boolean {
            return delegate.isExecuted
        }

        @Throws(IOException::class)
        override fun execute(): Response<NetworkResponse<T, U>> =
            try {
                success(delegate.execute())
            } catch (throwable: Throwable) {
                failure(throwable)
            }

        override fun cancel() {
            delegate.cancel()
        }

        override fun isCanceled(): Boolean {
            return delegate.isCanceled
        }

        override fun clone(): Call<NetworkResponse<T, U>> = CallbackCall(
            convertToErrorBodyWhenSuccessfulAndCannotParse,
            delegate.clone(),
            errorConverter
        )

        override fun request(): Request {
            return delegate.request()
        }

        override fun timeout(): Timeout {
            return delegate.timeout()
        }
    }
}
