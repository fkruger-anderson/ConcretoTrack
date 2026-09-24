package br.com.anderson.meuprimeiroprojeto

import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4

import org.junit.Test
import org.junit.runner.RunWith

import org.junit.Assert.*

/**
 * Teste de exemplo criado automaticamente pelo Android Studio junto com o projeto.
 *
 * Fica na pasta `src/androidTest`, que é a dos testes "instrumentados": rodam dentro de um celular
 * ou emulador de verdade, com acesso ao Android. São mais lentos que os unitários, mas conseguem
 * testar coisas como o banco de dados (Room). `@RunWith(AndroidJUnit4::class)` diz ao JUnit para
 * rodar este teste no aparelho.
 */
@RunWith(AndroidJUnit4::class)
class ExampleInstrumentedTest {
    /** Confere se o app que está sendo testado é mesmo o do motorista (pelo nome do pacote). */
    @Test
    fun useAppContext() {
        // Context of the app under test.
        // (O "contexto" do app que está sendo testado.)
        val appContext = InstrumentationRegistry.getInstrumentation().targetContext
        assertEquals("br.com.anderson.meuprimeiroprojeto", appContext.packageName)
    }
}
