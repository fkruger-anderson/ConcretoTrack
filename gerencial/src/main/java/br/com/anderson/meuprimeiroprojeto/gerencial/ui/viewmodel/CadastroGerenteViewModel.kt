package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/** Erros que só o servidor pode detectar (os de campo ficam em [CadastroGerenteUiState] à parte). */
enum class CadastroGerenteErro { EMAIL_EM_USO, SENHA_FRACA, EMAIL_INVALIDO, GENERICO }

/** Estado exibido por [CadastroGerenteActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroGerenteActivity]. */
data class CadastroGerenteUiState(
    val carregando: Boolean = false,
    val erroCampoNome: Boolean = false,
    val erroCampoEmail: Boolean = false,
    val erroCampoSenha: Boolean = false,
    val erroGeral: CadastroGerenteErro? = null,
    val sucesso: Evento<Unit>? = null
)

/**
 * Cadastra outro gerente a partir da aba Configurações, com o gerente atual já logado. É
 * [AndroidViewModel] porque [AutenticacaoRepositorio.cadastrarNovoGerente] precisa de um
 * `Context` (mesma razão do cadastro de motorista: abre uma segunda instância do Firebase
 * para não substituir a sessão de quem está criando a conta).
 */
class CadastroGerenteViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(CadastroGerenteUiState())
    val estado: LiveData<CadastroGerenteUiState> = _estado

    /** Valida nome, email e senha; se tudo ok, cria a conta do novo gerente sem trocar a sessão atual. */
    fun criar(nomeDigitado: String, emailDigitado: String, senha: String) {
        val nome = nomeDigitado.trim()
        val email = emailDigitado.trim()

        val nomeInvalido = nome.isEmpty()
        val emailInvalido = email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()
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
                AutenticacaoRepositorio.cadastrarNovoGerente(getApplication(), nome, email, senha)
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
