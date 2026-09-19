package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Converte um Task do Firebase (que usa listeners) em uma função suspend comum,
 * para poder usar com coroutines sem precisar de mais uma biblioteca.
 */
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { continuacao ->
    addOnSuccessListener { resultado -> continuacao.resume(resultado) }
    addOnFailureListener { erro -> continuacao.resumeWithException(erro) }
}
