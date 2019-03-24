package com.soywiz.korte.util

import com.soywiz.korte.internal.*
import kotlin.coroutines.*

class KorteDeferred<T> {
	private val lock = KorteLock()
	private var result: Result<T>? = null
	private val continuations = arrayListOf<Continuation<T>>()

	fun completeWith(result: Result<T>) {
		//println("completeWith: $result")
		lock {
			this.result = result
		}
		resolveIfRequired()
	}

	fun completeExceptionally(t: Throwable) = completeWith(Result.failure(t))
	fun complete(value: T) = completeWith(Result.success(value))

	suspend fun await(): T = suspendCoroutine { c ->
		lock {
			continuations += c
		}
		//println("await:$c")
		resolveIfRequired()
	}

	private fun resolveIfRequired() {
		if (lock { continuations.isNotEmpty() }) {
			//println("list")
			val result = lock { result }
			if (result != null) {
				val copy = lock { continuations.toList() }
				lock { continuations.clear() }

				for (v in copy) {
					//println("resume:$v")
					v.resumeWith(result)
				}
			} else {
				//println("result is null")
			}
		} else {
			//println("empty")
		}
	}

	fun toContinuation(coroutineContext: CoroutineContext) = object : Continuation<T> {
		override val context: CoroutineContext = coroutineContext
		override fun resumeWith(result: Result<T>) = completeWith(result)
	}

	companion object {
		fun <T> asyncImmediately(coroutineContext: CoroutineContext, callback: suspend () -> T): KorteDeferred<T> =
			KorteDeferred<T>().also { deferred ->
				callback.startCoroutine(object : Continuation<T> {
					override val context: CoroutineContext = coroutineContext
					override fun resumeWith(result: Result<T>) = deferred.completeWith(result)
				})
			}
	}
}