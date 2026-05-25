package eu.kanade.tachiyomi.util.lang

import kotlinx.coroutines.suspendCancellableCoroutine
import rx.Observable
import rx.Subscriber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

suspend fun <T> Observable<T>.awaitSingle(): T = suspendCancellableCoroutine { cont ->
    var completed = false
    val subscription = this.subscribe(
        object : Subscriber<T>() {
            override fun onNext(t: T) {
                if (!completed) {
                    completed = true
                    cont.resume(t)
                }
            }

            override fun onError(e: Throwable) {
                if (!completed) {
                    completed = true
                    cont.resumeWithException(e)
                }
            }

            override fun onCompleted() {
                if (!completed) {
                    completed = true
                    cont.resumeWithException(
                        NoSuchElementException("Observable completed without emitting a value")
                    )
                }
            }
        }
    )

    cont.invokeOnCancellation {
        subscription.unsubscribe()
    }
}