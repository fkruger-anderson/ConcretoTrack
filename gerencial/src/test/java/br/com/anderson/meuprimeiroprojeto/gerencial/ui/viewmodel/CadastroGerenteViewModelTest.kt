package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência dos campos do cadastro de gerente ([CadastroGerenteViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste.
 *
 * O formulário "padrão" destes testes tem a senha curta demais, para a conferência sempre parar
 * antes de chamar o Firebase de verdade.
 */
class CadastroGerenteViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    private val viewModel = CadastroGerenteViewModel()

    /** Preenche o formulário; cada teste troca só o campo que quer conferir. */
    private fun criar(
        nome: String = "Carlos Lima",
        email: String = "carlos@usina.com.br",
        senha: String = "12345"
    ) = viewModel.criar(nome, email, senha)

    /** O estado atual (o `!!` diz ao Kotlin "tenho certeza que não é nulo aqui"). */
    private fun estado() = viewModel.estado.value!!

    /** Nome vazio é recusado. */
    @Test
    fun nomeVazioMarcaErro() {
        criar(nome = "")
        assertTrue(estado().erroCampoNome)
    }

    /** Email sem @ é recusado. */
    @Test
    fun emailSemArrobaMarcaErro() {
        criar(email = "carlos.usina.com.br")
        assertTrue(estado().erroCampoEmail)
    }

    /** Email bem formado não é marcado como erro. */
    @Test
    fun emailValidoNaoMarcaErro() {
        criar()
        assertFalse(estado().erroCampoEmail)
    }

    /** Senha com menos de 6 caracteres é recusada. */
    @Test
    fun senhaCurtaMarcaErro() {
        criar(senha = "12345")
        assertTrue(estado().erroCampoSenha)
    }

    /** Senha com 6 caracteres é aceita (o nome vazio só está aí para não chamar o Firebase). */
    @Test
    fun senhaComSeisCaracteresNaoMarcaErro() {
        criar(nome = "", senha = "123456")
        assertFalse(estado().erroCampoSenha)
    }
}
