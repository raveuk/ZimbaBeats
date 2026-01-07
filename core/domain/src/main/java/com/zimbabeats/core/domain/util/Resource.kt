package com.zimbabeats.core.domain.util

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error(val message: String, val cause: Throwable? = null) : Resource<Nothing>()
    data object Loading : Resource<Nothing>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        else -> null
    }

    fun getOrThrow(): T = when (this) {
        is Success -> data
        is Error -> throw cause ?: Exception(message)
        Loading -> throw IllegalStateException("Resource is still loading")
    }

    fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data))
        is Error -> Error(message, cause)
        Loading -> Loading
    }

    companion object {
        fun <T> success(data: T): Resource<T> = Success(data)
        fun error(message: String, cause: Throwable? = null): Resource<Nothing> = Error(message, cause)
        fun loading(): Resource<Nothing> = Loading
    }
}
