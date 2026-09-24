package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência dos campos do cadastro de pedido ([NovoPedidoViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste.
 *
 * O formulário "padrão" destes testes está todo certo, MENOS a data de entrega (fica vazia). Assim
 * a conferência sempre para antes de cadastrar no Firebase de verdade — por isso nenhum teste
 * escolhe uma data.
 */
class NovoPedidoViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    private val viewModel = NovoPedidoViewModel()

    /** Preenche o formulário; cada teste troca só o campo que quer conferir. */
    private fun salvar(
        documento: String = "529.982.247-25",
        nome: String = "Construtora Silva",
        cep: String = "89150-000",
        rua: String = "Rua das Flores",
        numero: String = "120",
        bairro: String = "Centro",
        cidade: String = "Presidente Getúlio",
        uf: String = "SC",
        quantidade: String = "10",
        distancia: String = "15"
    ) = viewModel.salvar(documento, nome, cep, rua, numero, bairro, cidade, uf, quantidade, distancia, null)

    /** O estado atual (o `!!` diz ao Kotlin "tenho certeza que não é nulo aqui"). */
    private fun estado() = viewModel.estado.value!!

    /** CPF com o dígito verificador errado é recusado. */
    @Test
    fun documentoInvalidoMarcaErro() {
        salvar(documento = "529.982.247-24")
        assertTrue(estado().erroDocumento)
    }

    /** CNPJ válido é aceito (pedido de pessoa jurídica). */
    @Test
    fun cnpjValidoNaoMarcaErro() {
        salvar(documento = "11.222.333/0001-81")
        assertFalse(estado().erroDocumento)
    }

    /** Nome vazio é recusado. */
    @Test
    fun nomeVazioMarcaErro() {
        salvar(nome = "")
        assertTrue(estado().erroNome)
    }

    /** CEP com 7 números é recusado. */
    @Test
    fun cepIncompletoMarcaErroNoEndereco() {
        salvar(cep = "8915000")
        assertTrue(estado().erroEndereco)
    }

    /** UF que não existe é recusada. */
    @Test
    fun ufInexistenteMarcaErroNoEndereco() {
        salvar(uf = "XX")
        assertTrue(estado().erroEndereco)
    }

    /** UF digitada em minúsculas é aceita. */
    @Test
    fun ufMinusculaNaoMarcaErro() {
        salvar(uf = "sc")
        assertFalse(estado().erroEndereco)
    }

    /** Quantidade vazia, zero, negativa ou que não é número é recusada. */
    @Test
    fun quantidadesInvalidasMarcamErro() {
        for (quantidade in listOf("", "0", "-1", "abc")) {
            salvar(quantidade = quantidade)
            assertTrue("quantidade '$quantidade'", estado().erroQuantidade)
        }
    }

    /** Quantidade com vírgula (o jeito brasileiro) é aceita. */
    @Test
    fun quantidadeComVirgulaNaoMarcaErro() {
        salvar(quantidade = "2,5")
        assertFalse(estado().erroQuantidade)
    }

    /** Distância vazia é recusada. */
    @Test
    fun distanciaVaziaMarcaErro() {
        salvar(distancia = "")
        assertTrue(estado().erroDistanciaKm)
    }

    /** Sem data de entrega escolhida, o pedido é recusado. */
    @Test
    fun semDataDeEntregaMarcaErro() {
        salvar()
        assertTrue(estado().erroDataEntrega)
    }

    /** Com algum campo errado, nada é enviado: a rodinha de carregando não aparece. */
    @Test
    fun formularioComErroNaoEnvia() {
        salvar()
        assertFalse(estado().carregando)
    }
}
