package com.example.authenticator.common.basic

sealed interface OperationResult<out T, out E> {
    data class Success<T>(val value: T) : OperationResult<T, Nothing>
    data class Failure<E>(val error: E) : OperationResult<Nothing, E>
}
