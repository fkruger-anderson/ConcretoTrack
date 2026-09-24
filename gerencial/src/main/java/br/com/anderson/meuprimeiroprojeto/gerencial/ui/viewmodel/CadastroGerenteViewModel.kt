package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.EmailUtils
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/**
 * Erros que só o servidor consegue descobrir (email já usado, senha fraca, email inválido para o
 * Firebase, falha de internet). Os erros de preenchimento ficam em [CadastroGerenteUiState].
 */
enum class CadastroGerenteErro { EMAIL_EM_USO, SENHA_FRACA, EMAIL_INVALIDO, GENERICO }

/**
 * O estado da tela de cadastro de gerente
 * ([CadastroGerenteActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroGerenteActivity]).
 */
data class CadastroGerenteUiState(
    /** `true` enquanto o cadastro está sendo enviado. */
    val carregando: Boolean = false,
    /** Erros de preenchimento: nome, email e senha. */
    val erroCampoNome: Boolean = false,
    val erroCampoEmail: Boolean = false,
    val erroCampoSenha: Boolean = false,
    /** Erro vindo do servidor, ou `null`. */
    val erroGeral: CadastroGerenteErro? = null,
    /** Aviso de sucesso (a tela fecha), tratado uma vez só. */
    val sucesso: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de cadastro de outro gerente, usada por um gerente já logado.
 *
 * [AutenticacaoRepositorio.cadastrarNovoGerente] não precisa receber `Context`: a segunda
 * conexão com o Firebase é aberta usando o contexto que o próprio Firebase já tem
 * ([com.google.firebase.FirebaseApp.applicationContext]). Por isso este é um `ViewModel` comum.
 */
class CadastroGerenteViewModel : ViewModel() {

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(CadastroGerenteUiState())
    /** O estado atual da tela; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<CadastroGerenteUiState> = _estado

    /**
     * Confere os campos e, se estiverem certos, cria a conta do novo gerente sem deslogar quem está
     * cadastrando.
     *
     * - Nome: não pode ser vazio.
     * - Email: precisa ter formato de email (conferido por [EmailUtils], com a mesma regra do Android).
     * - Senha: pelo menos 6 caracteres (é o mínimo que o Firebase aceita).
     *
     * Cada `catch` transforma um erro do Firebase num tipo de [CadastroGerenteErro].
     */
    fun criar(nomeDigitado: String, emailDigitado: String, senha: String) {
        val nome = nomeDigitado.trim()
        val email = emailDigitado.trim()

        val nomeInvalido = nome.isEmpty()
        val emailInvalido = email.isEmpty() || !EmailUtils.valido(email)
        val senhaInvalida = senha.length < 6

        if (nomeInvalido || emailInvalido || senhaInvalida) {
            _estado.value = _estado.value?.copy(
                erroCampoNome = nomeInvalido,
                erroCampoEmail = emailInvalido,
                erroCampoSenha = senhaInvalida
            )
            return
        }

        _estado.value = _estado.value?.copy(
            carregando = true,
            erroCampoNome = false,
            erroCampoEmail = false,
            erroCampoSenha = false,
            erroGeral = null
        )
        viewModelScope.launch {
            try {
                AutenticacaoRepositorio.cadastrarNovoGerente(nome, email, senha)
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(Unit))
            } catch (erro: FirebaseAuthUserCollisionException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroGerenteErro.EMAIL_EM_USO)
            } catch (erro: FirebaseAuthWeakPasswordException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroGerenteErro.SENHA_FRACA)
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                _estado.value =
                    _estado.value?.copy(carregando = false, erroGeral = CadastroGerenteErro.EMAIL_INVALIDO)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroGerenteErro.GENERICO)
            }
        }
    }
}
