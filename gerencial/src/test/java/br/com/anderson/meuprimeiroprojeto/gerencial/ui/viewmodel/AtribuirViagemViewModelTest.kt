package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaComId
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaPerfil
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.Caminhao
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência da quantidade na tela de atribuir viagem ([AtribuirViagemViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste.
 *
 * Só são testadas quantidades inválidas: com uma quantidade válida, o ViewModel chamaria o
 * Firebase de verdade (a conferência do saldo é feita no servidor).
 */
class AtribuirViagemViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    private val viewModel = AtribuirViagemViewModel()
    private val motorista = MotoristaComId("uid-ana", MotoristaPerfil(nome = "Ana Souza"))
    private val caminhao = Caminhao("ABC1D23", "Mercedes-Benz", "Atego 2426", 2020)

    /** Quantidade vazia, zero, negativa ou que não é número é recusada, sem enviar nada. */
    @Test
    fun quantidadesInvalidasMarcamErroSemEnviar() {
        for (quantidade in listOf("", "0", "-2", "abc")) {
            viewModel.atribuir(7, motorista, caminhao, quantidade)
            assertTrue("quantidade '$quantidade'", viewModel.estado.value!!.erroQuantidade)
            assertFalse("quantidade '$quantidade'", viewModel.estado.value!!.enviando)
        }
    }
}
