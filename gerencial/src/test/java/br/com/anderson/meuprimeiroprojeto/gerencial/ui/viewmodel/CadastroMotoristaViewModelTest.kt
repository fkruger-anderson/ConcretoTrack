package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.arch.core.executor.testing.InstantTaskExecutorRule
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/**
 * Testes da conferência dos campos do cadastro de motorista ([CadastroMotoristaViewModel]).
 *
 * `InstantTaskExecutorRule` faz o `LiveData` aceitar mudanças na hora, na thread do teste (no
 * computador não existe a thread principal do Android).
 *
 * Cuidado: se todos os campos estiverem certos, o ViewModel tenta cadastrar no Firebase de verdade.
 * Por isso o formulário "padrão" destes testes tem a senha curta demais (5 caracteres): assim a
 * conferência sempre para antes de usar a internet. O teste da senha válida deixa outro campo
 * errado pelo mesmo motivo.
 */
class CadastroMotoristaViewModelTest {

    @get:Rule
    val regraLiveData = InstantTaskExecutorRule()

    private val viewModel = CadastroMotoristaViewModel()

    /** Preenche o formulário; cada teste troca só o campo que quer conferir. */
    private fun criar(
        nome: String = "Ana Souza",
        cpf: String = "529.982.247-25",
        email: String = "ana@usina.com.br",
        senha: String = "12345"
    ) = viewModel.criar(nome, cpf, email, senha)

    /** O estado atual (o `!!` diz ao Kotlin "tenho certeza que não é nulo aqui"). */
    private fun estado() = viewModel.estado.value!!

    /** Nome só com espaços conta como vazio. */
    @Test
    fun nomeEmBrancoMarcaErro() {
        criar(nome = "   ")
        assertTrue(estado().erroCampoNome)
    }

    /** CPF com 10 números não é CPF. */
    @Test
    fun cpfIncompletoMarcaErro() {
        criar(cpf = "529.982.247-2")
        assertTrue(estado().erroCampoCpf)
    }

    /** CPF com o dígito verificador errado é recusado. */
    @Test
    fun cpfComDigitoErradoMarcaErro() {
        criar(cpf = "529.982.247-24")
        assertTrue(estado().erroCampoCpf)
    }

    /** CPF válido não é marcado como erro. */
    @Test
    fun cpfValidoNaoMarcaErro() {
        criar()
        assertFalse(estado().erroCampoCpf)
    }

    /** Email sem a parte depois do @ é recusado. */
    @Test
    fun emailSemDominioMarcaErro() {
        criar(email = "ana@")
        assertTrue(estado().erroCampoEmail)
    }

    /** Email bem formado não é marcado como erro. */
    @Test
    fun emailValidoNaoMarcaErro() {
        criar()
        assertFalse(estado().erroCampoEmail)
    }

    /** Senha com menos de 6 caracteres (o mínimo do Firebase) é recusada. */
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

    /** Com algum campo errado, nada é enviado: a rodinha de carregando não aparece. */
    @Test
    fun formularioComErroNaoEnvia() {
        criar()
        assertFalse(estado().carregando)
    }
}
