package tj.app.quran_todo.common.utils

sealed class Resource<out T> {
    data class Success<T>(val data: T) : Resource<T>()
    data class Error<T>(val errorMessage: String): Resource<T>()
    class Loading<T> : Resource<T>()
    class Idle<T> : Resource<T>()
}