package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Tradutor" entre dois jeitos de esperar uma resposta da internet.
 *
 * O Firebase devolve um `Task`: um pedido que vai terminar "algum dia". Para saber quando, você
 * registra "ouvintes" (listeners): um que é chamado se der certo e outro se der errado. Funciona,
 * mas o código fica cheio de chaves dentro de chaves.
 *
 * As coroutines do Kotlin deixam escrever o mesmo código de cima para baixo, como se a resposta
 * fosse instantânea: `val documento = referencia.get().aguardar()`. Esta função faz a ponte:
 * "pausa" a coroutine até o `Task` terminar e então devolve o resultado (ou lança o erro).
 * Enquanto está pausada, a tela continua respondendo normalmente.
 *
 * Detalhes da declaração:
 * - `suspend`: a função pode pausar; por isso só pode ser chamada de dentro de uma coroutine
 *   (ou de outra função `suspend`).
 * - `Task<T>.aguardar()`: é uma "função de extensão". O `Task<T>.` antes do nome faz com que ela
 *   seja chamada como se fosse um método do próprio `Task`, mesmo sem termos escrito essa classe.
 * - `<T>`: funciona para qualquer tipo de resultado (um documento, uma lista, um usuário...).
 */
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { continuacao ->
    // Deu certo: "acorda" a coroutine entregando o resultado.
    addOnSuccessListener { resultado -> continuacao.resume(resultado) }
    // Deu errado: "acorda" a coroutine lançando o erro, que cai no `catch` de quem chamou.
    addOnFailureListener { erro -> continuacao.resumeWithException(erro) }
}
