package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * "Tradutor" entre dois jeitos de esperar uma resposta da internet (o mesmo arquivo existe no app
 * do motorista).
 *
 * O Firebase devolve um `Task`: um pedido que vai terminar "algum dia", avisando por "ouvintes"
 * (listeners) de sucesso e de erro. Esta função deixa escrever `val x = tarefa.aguardar()`, de cima
 * para baixo: ela "pausa" a coroutine até o `Task` terminar e então devolve o resultado ou lança o
 * erro. Enquanto está pausada, a tela continua respondendo.
 *
 * - `suspend`: a função pode pausar, por isso só é chamada de dentro de uma coroutine.
 * - `Task<T>.aguardar()`: "função de extensão" — é chamada como se fosse um método do `Task`.
 * - `<T>`: funciona para qualquer tipo de resultado.
 */
suspend fun <T> Task<T>.aguardar(): T = suspendCancellableCoroutine { continuacao ->
    // Deu certo: "acorda" a coroutine entregando o resultado.
    addOnSuccessListener { resultado -> continuacao.resume(resultado) }
    // Deu errado: "acorda" a coroutine lançando o erro, que cai no `catch` de quem chamou.
    addOnFailureListener { erro -> continuacao.resumeWithException(erro) }
}
