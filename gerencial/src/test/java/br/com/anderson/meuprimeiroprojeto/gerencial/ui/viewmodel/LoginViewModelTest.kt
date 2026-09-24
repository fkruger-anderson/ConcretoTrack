package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência de campos do login do gerente ([LoginViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste. Só os
 * casos de campo vazio são testados, porque os outros chamam o Firebase de verdade.
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
        viewModel.entrar("carlos@usina.com.br", "")
        assertEquals(LoginErro.CAMPOS_VAZIOS, viewModel.estado.value?.erro)
    }
}
