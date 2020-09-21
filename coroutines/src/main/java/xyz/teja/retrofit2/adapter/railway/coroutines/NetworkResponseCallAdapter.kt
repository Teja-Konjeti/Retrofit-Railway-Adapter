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
import okhttp3.ResponseBody.Companion.toResponseBody
import okio.Timeout
import retrofit2.*
import xyz.teja.retrofit2.adapter.railway.NetworkResponse
import java.io.IOException
import java.lang.reflect.Type
import java.util.*
import kotlin.jvm.Throws

internal class NetworkResponseCallAdapter<T : Any, U : Any>(
    private val delegateAdapter: CallAdapter<ResponseBody, Call<ResponseBody>>,
    private val successConverter: Converter<ResponseBody, T>,
    private val errorConverter: Converter<ResponseBody, U>,
    private val convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
) : CallAdapter<ResponseBody, Call<NetworkResponse<T, U>>> {
    override fun adapt(call: Call<ResponseBody>): Call<NetworkResponse<T, U>> =
        CallbackCall(
            convertToErrorBodyWhenSuccessfulAndCannotParse,
            delegateAdapter.adapt(call),
            successConverter,
            errorConverter,
        )

    override fun responseType(): Type = ResponseBody::class.java

    internal class CallbackCall<T : Any, U : Any>(
        private val convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
        private val delegate: Call<ResponseBody>,
        private val successConverter: Converter<ResponseBody, T>,
        private val errorConverter: Converter<ResponseBody, U>,
    ) : Call<NetworkResponse<T, U>> {
        override fun enqueue(callback: Callback<NetworkResponse<T, U>>) {
            Objects.requireNonNull(callback, "callback == null")
            delegate.enqueue(
                object : Callback<ResponseBody> {
                    override fun onResponse(
                        call: Call<ResponseBody>,
                        response: Response<ResponseBody>
                    ) {
                        callback.onResponse(this@CallbackCall, success(response))
                    }

                    override fun onFailure(call: Call<ResponseBody>, throwable: Throwable) {
                        callback.onResponse(this@CallbackCall, failure(throwable))
                    }
                })
        }

        private fun success(response: Response<ResponseBody>): Response<NetworkResponse<T, U>> {
            return wrapNetworkResponseInResponse(
                convertResponseToNetworkResponse(
                    response,
                    convertToErrorBodyWhenSuccessfulAndCannotParse
                ),
                response
            )
        }

        private fun failure(throwable: Throwable): Response<NetworkResponse<T, U>> {
            return wrapNetworkResponseInResponse(
                throwableToNetworkResponse(throwable),
                null
            )
        }

        private fun convertResponseToNetworkResponse(
            response: Response<ResponseBody>,
            convertToErrorBodyWhenSuccessfulAndCannotParse: Boolean,
        ): NetworkResponse<T, U> {
            when {
                response.isSuccessful -> {
                    val responseBody = response.body()
                    val body = responseBody?.string()
                    responseBody?.close()

                    if (body != null) {
                        val success = try {
                            successConverter.convert(body.toResponseBody())
                        } catch (exception: Exception) {
                            null
                        }

                        if (success != null || !convertToErrorBodyWhenSuccessfulAndCannotParse) {
                            return NetworkResponse.Success(success)
                        } else {
                            try {
                                val error = errorConverter.convert(body.toResponseBody())
                                if (error != null) {
                                    return NetworkResponse.ServerError(error, response.code())
                                }
                            } catch (e: Exception) {
                                print(e)
                            }
                        }
                    }

                    return NetworkResponse.Success(null)
                }
                else -> return errorBodyToNetworkResponse(response)
            }
        }

        private fun throwableToNetworkResponse(
            throwable: Throwable,
        ): NetworkResponse<T, U> {
            return if (throwable is HttpException) {
                val response = throwable.response()
                if (response != null) {
                    errorBodyToNetworkResponse(response)
                } else {
                    NetworkResponse.NetworkError(throwable)
                }
            } else if (throwable is IOException) {
                NetworkResponse.NetworkError(throwable)
            } else {
                throw throwable
            }
        }

        /// Will try to parse to Error Body and return a Server Error if parsing is successful
        /// If parsing fails, returns a Server Error for codes > 100 and Network Error otherwise
        private fun errorBodyToNetworkResponse(
            response: Response<*>,
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
            retrofitResponse: Response<ResponseBody>?
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
            successConverter,
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
