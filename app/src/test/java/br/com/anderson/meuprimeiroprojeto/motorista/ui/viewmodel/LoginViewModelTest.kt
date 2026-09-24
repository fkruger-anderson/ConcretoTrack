package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência de campos do login do motorista ([LoginViewModel]).
 *
 * O `LiveData` do ViewModel só aceita mudanças vindas da thread principal do Android, que não
 * existe quando o teste roda no computador. A regra `InstantTaskExecutorRule` resolve isso: ela
 * faz o `LiveData` aceitar a mudança na hora, na thread do próprio teste. `@get:Rule` liga a regra
 * antes de cada teste.
 *
 * Só são testados os casos em que o login para antes de usar a internet (campos vazios), porque
 * os outros caminhos chamam o Firebase de verdade.
 */
class LoginViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    /** Email vazio: nem tenta entrar, mostra o erro de campos vazios. */
    @Test
    fun emailVazioMostraErroDeCamposVazios() {
        val viewModel = LoginViewModel()
        viewModel.entrar("", "123456")
        assertEquals(LoginErro.CAMPOS_VAZIOS, viewModel.estado.value?.erro)
    }

    /** Senha vazia: nem tenta entrar, mostra o erro de campos vazios. */
    @Test
    fun senhaVaziaMostraErroDeCamposVazios() {
        val viewModel = LoginViewModel()
        viewModel.entrar("ana@usina.com.br", "")
        assertEquals(LoginErro.CAMPOS_VAZIOS, viewModel.estado.value?.erro)
    }
}
