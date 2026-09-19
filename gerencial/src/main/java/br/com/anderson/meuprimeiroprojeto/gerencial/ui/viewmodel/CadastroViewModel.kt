package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Patterns
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/** Erros que só o servidor pode detectar (os de campo ficam em [CadastroUiState] à parte). */
enum class CadastroErro { EMAIL_EM_USO, SENHA_FRACA, EMAIL_INVALIDO, GENERICO }

/** Estado exibido por [CadastroActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.CadastroActivity]. */
data class CadastroUiState(
    val carregando: Boolean = false,
    val erroCampoNome: Boolean = false,
    val erroCampoEmail: Boolean = false,
    val erroCampoSenha: Boolean = false,
    val erroGeral: CadastroErro? = null,
    val sucesso: Evento<Unit>? = null
)

/** Cria a conta do gerente no Firebase Auth e salva o perfil (nome) no Firestore. */
class CadastroViewModel : ViewModel() {

    private val _estado = MutableLiveData(CadastroUiState())
    val estado: LiveData<CadastroUiState> = _estado

    /** Valida nome, email e senha; se tudo ok, cria a conta do gerente no Firebase. */
    fun criarConta(nome: String, email: String, senha: String) {
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
                AutenticacaoRepositorio.cadastrar(nome, email, senha)
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(Unit))
            } catch (erro: FirebaseAuthUserCollisionException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroErro.EMAIL_EM_USO)
            } catch (erro: FirebaseAuthWeakPasswordException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroErro.SENHA_FRACA)
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroErro.EMAIL_INVALIDO)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erroGeral = CadastroErro.GENERICO)
            }
        }
    }
}
