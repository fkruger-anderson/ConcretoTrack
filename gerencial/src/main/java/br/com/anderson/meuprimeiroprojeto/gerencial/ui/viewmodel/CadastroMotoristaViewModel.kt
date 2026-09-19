package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.app.Application
import android.util.Patterns
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/** Erros que só o servidor pode detectar (os de campo ficam em [CadastroMotoristaUiState] à parte). */
enum class CadastroMotoristaErro { EMAIL_EM_USO, SENHA_FRACA, EMAIL_INVALIDO, GENERICO }

/** Estado exibido por [CadastroMotoristaActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroMotoristaActivity]. */
data class CadastroMotoristaUiState(
    val carregando: Boolean = false,
    val erroCampoNome: Boolean = false,
    val erroCampoCpf: Boolean = false,
    val erroCampoEmail: Boolean = false,
    val erroCampoSenha: Boolean = false,
    val erroGeral: CadastroMotoristaErro? = null,
    val sucesso: Evento<Unit>? = null
)

/**
 * Cria o login (email/senha) e o perfil do motorista. É [AndroidViewModel] porque
 * [MotoristaRepositorio.cadastrar] precisa de um `Context` (pra abrir uma segunda instância
 * do Firebase e não substituir a sessão do gerente logado — ver o comentário lá).
 */
class CadastroMotoristaViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(CadastroMotoristaUiState())
    val estado: LiveData<CadastroMotoristaUiState> = _estado

    /** Valida nome, CPF (dígitos verificadores), email e senha; se tudo ok, cadastra o motorista. */
    fun criar(nomeDigitado: String, cpfDigitado: String, emailDigitado: String, senha: String) {
        val nome = nomeDigitado.trim()
        val cpf = DocumentoUtils.apenasDigitos(cpfDigitado)
        val email = emailDigitado.trim()

        val nomeInvalido = nome.isEmpty()
        val cpfInvalido = cpf.length != 11 || !DocumentoUtils.documentoValido(cpf)
        val emailInvalido = email.isEmpty() || !Patterns.EMAIL_ADDRESS.matcher(email).matches()
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
                MotoristaRepositorio.cadastrar(getApplication(), nome, cpf, email, senha)
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
