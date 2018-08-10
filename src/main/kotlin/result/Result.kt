package result

import result.Result.Failure
import result.Result.Success

sealed class Result<out F, out S> {
    data class Success<out S>(val value: S) : Result<Nothing, S>()
    data class Failure<out F>(val reason: F) : Result<F, Nothing>()
}

fun <F, S, T> Result<F, S>.map(f: (S) -> T): Result<F, T> =
        when (this) {
            is Success<S> -> Success(f(this.value))
            is Failure<F> -> this
        }

fun <F, S, T> Result<F, S>.flatMap(f: (S) -> Result<F, T>): Result<F, T> =
        when (this) {
            is Success<S> -> f(this.value)
            is Failure<F> -> this
        }

fun <F, S, FINAL> Result<F, S>.fold(failure: (F) -> FINAL, success: (S) -> FINAL) : FINAL = this.map(success).orElse(failure)

fun <F, S> Result<F, S>.orElse(f: (F) -> S): S =
        when (this) {
            is Success<S> -> this.value
            is Failure<F> -> f(this.reason)
        }

fun <T> T.asSuccess() = Success(this)
