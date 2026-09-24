package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência dos campos do cadastro de caminhão ([CadastroCaminhaoViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste.
 *
 * O formulário "padrão" destes testes tem a marca vazia, para a conferência sempre parar antes de
 * chamar o Firebase de verdade.
 */
class CadastroCaminhaoViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    private val viewModel = CadastroCaminhaoViewModel()

    /** Preenche o formulário; cada teste troca só o campo que quer conferir. */
    private fun criar(
        placa: String = "ABC1D23",
        marca: String = "",
        modelo: String = "Atego 2426",
        ano: String = "2020"
    ) = viewModel.criar(placa, marca, modelo, ano)

    /** O estado atual (o `!!` diz ao Kotlin "tenho certeza que não é nulo aqui"). */
    private fun estado() = viewModel.estado.value!!

    /** Placa fora do padrão (só 2 letras) é recusada. */
    @Test
    fun placaForaDoPadraoMarcaErro() {
        criar(placa = "AB1234")
        assertTrue(estado().erroCampoPlaca)
    }

    /** Placa no padrão antigo (3 letras e 4 números) é aceita. */
    @Test
    fun placaAntigaNaoMarcaErro() {
        criar(placa = "ABC1234")
        assertFalse(estado().erroCampoPlaca)
    }

    /** Placa Mercosul digitada em minúsculas é aceita (vira maiúscula antes de conferir). */
    @Test
    fun placaMercosulMinusculaNaoMarcaErro() {
        criar(placa = "abc1d23")
        assertFalse(estado().erroCampoPlaca)
    }

    /** Marca vazia é recusada. */
    @Test
    fun marcaVaziaMarcaErro() {
        criar(marca = "")
        assertTrue(estado().erroMarcaModelo)
    }

    /** Ano antes de 1970 é recusado. */
    @Test
    fun anoAntesDe1970MarcaErro() {
        criar(ano = "1969")
        assertTrue(estado().erroCampoAno)
    }

    /** Ano depois de 2100 é recusado. */
    @Test
    fun anoDepoisDe2100MarcaErro() {
        criar(ano = "2101")
        assertTrue(estado().erroCampoAno)
    }

    /** Ano que não é número é recusado. */
    @Test
    fun anoQueNaoEhNumeroMarcaErro() {
        criar(ano = "dois mil")
        assertTrue(estado().erroCampoAno)
    }

    /** Os anos-limite, 1970 e 2100, são aceitos. */
    @Test
    fun anosNosLimitesNaoMarcamErro() {
        criar(ano = "1970")
        assertFalse(estado().erroCampoAno)
        criar(ano = "2100")
        assertFalse(estado().erroCampoAno)
    }

    /** Com algum campo errado, nada é enviado: a rodinha de carregando não aparece. */
    @Test
    fun formularioComErroNaoEnvia() {
        criar()
        assertFalse(estado().carregando)
    }
}
