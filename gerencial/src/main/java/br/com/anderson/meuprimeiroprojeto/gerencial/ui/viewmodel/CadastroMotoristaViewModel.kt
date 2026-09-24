package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.EmailUtils
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/**
 * Erros que só o servidor consegue descobrir (email já usado, senha fraca, email inválido para o
 * Firebase, falha de internet). Os erros de preenchimento ficam em [CadastroMotoristaUiState].
 */
enum class CadastroMotoristaErro { EMAIL_EM_USO, SENHA_FRACA, EMAIL_INVALIDO, GENERICO }

/**
 * O estado da tela de cadastro de motorista
 * ([CadastroMotoristaActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroMotoristaActivity]).
 */
data class CadastroMotoristaUiState(
    /** `true` enquanto o cadastro está sendo enviado. */
    val carregando: Boolean = false,
    /** Erros de preenchimento: nome, CPF, email e senha. */
    val erroCampoNome: Boolean = false,
    val erroCampoCpf: Boolean = false,
    val erroCampoEmail: Boolean = false,
    val erroCampoSenha: Boolean = false,
    /** Erro vindo do servidor, ou `null`. */
    val erroGeral: CadastroMotoristaErro? = null,
    /** Aviso de sucesso (a tela fecha), tratado uma vez só. */
    val sucesso: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de cadastro de motorista: cria o login (email e senha) e o perfil (nome e
 * CPF) do motorista.
 *
 * [MotoristaRepositorio.cadastrar] não precisa receber `Context`: a segunda conexão com o Firebase
 * usa o contexto que o próprio Firebase já tem. Por isso este é um `ViewModel` comum.
 */
class CadastroMotoristaViewModel : ViewModel() {

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(CadastroMotoristaUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<CadastroMotoristaUiState> = _estado

    /**
     * Confere os campos e, se estiverem certos, cadastra o motorista.
     *
     * - Nome: não pode ser vazio.
     * - CPF: precisa ter 11 números e dígitos verificadores certos (motorista é sempre pessoa
     *   física, então CNPJ não serve). É guardado só com os números.
     * - Email: precisa ter formato de email (conferido por [EmailUtils]).
     * - Senha: pelo menos 6 caracteres (o mínimo do Firebase).
     *
     * Cada `catch` transforma um erro do Firebase num tipo de [CadastroMotoristaErro].
     */
    fun criar(nomeDigitado: String, cpfDigitado: String, emailDigitado: String, senha: String) {
        val nome = nomeDigitado.trim()
        val cpf = DocumentoUtils.apenasDigitos(cpfDigitado)
        val email = emailDigitado.trim()

        val nomeInvalido = nome.isEmpty()
        val cpfInvalido = cpf.length != 11 || !DocumentoUtils.documentoValido(cpf)
        val emailInvalido = email.isEmpty() || !EmailUtils.valido(email)
        val senhaInvalida = senha.length < 6

        if (nomeInvalido || cpfInvalido || emailInvalido || senhaInvalida) {
            _estado.value = _estado.value?.copy(
                erroCampoNome = nomeInvalido,
                erroCampoCpf = cpfInvalido,
                erroCampoEmail = emailInvalido,
                erroCampoSenha = senhaInvalida
            )
            return
        }

        _estado.value = _estado.value?.copy(
            carregando = true,
            erroCampoNome = false,
            erroCampoCpf = false,
            erroCampoEmail = false,
            erroCampoSenha = false,
            erroGeral = null
        )
        viewModelScope.launch {
            try {
                MotoristaRepositorio.cadastrar(nome, cpf, email, senha)
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(Unit))
            } catch (erro: FirebaseAuthUserCollisionException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroMotoristaErro.EMAIL_EM_USO)
            } catch (erro: FirebaseAuthWeakPasswordException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroMotoristaErro.SENHA_FRACA)
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                _estado.value =
                    _estado.value?.copy(carregando = false, erroGeral = CadastroMotoristaErro.EMAIL_INVALIDO)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroMotoristaErro.GENERICO)
            }
        }
    }
}
