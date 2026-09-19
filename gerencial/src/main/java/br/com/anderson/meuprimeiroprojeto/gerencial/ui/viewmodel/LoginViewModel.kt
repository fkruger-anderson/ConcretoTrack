package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch

/** Os três jeitos de dar errado no login — a Activity escolhe o texto certo pra cada um. */
enum class LoginErro { CAMPOS_VAZIOS, CREDENCIAIS_INVALIDAS, GENERICO }

/** Estado exibido por [LoginActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.LoginActivity]. */
data class LoginUiState(
    val carregando: Boolean = false,
    val erro: LoginErro? = null,
    val sucesso: Evento<Unit>? = null
)

/** Autentica o gerente com Firebase Auth. Sem cache local aqui, então nem precisa de Context. */
class LoginViewModel : ViewModel() {

    private val _estado = MutableLiveData(LoginUiState())
    val estado: LiveData<LoginUiState> = _estado

    /** O Firebase Auth já lembra a sessão sozinho; se já tem usuário, a Activity pula o login. */
    fun jaEstaLogado(): Boolean = AutenticacaoRepositorio.usuarioAtual != null

    /** Valida que os campos não estão vazios e autentica; erros de credencial e usuário viram um único erro genérico pra não vazar qual dado estava errado. */
    fun entrar(email: String, senha: String) {
        if (email.isEmpty() || senha.isEmpty()) {
            _estado.value = _estado.value?.copy(erro = LoginErro.CAMPOS_VAZIOS)
            return
        }

        _estado.value = _estado.value?.copy(carregando = true, erro = null)
        viewModelScope.launch {
            try {
                AutenticacaoRepositorio.entrar(email, senha)
                _estado.value = _estado.value?.copy(carregando = false, sucesso = Evento(Unit))
            } catch (erro: FirebaseAuthInvalidUserException) {
                _estado.value = _estado.value?.copy(carregando = false, erro = LoginErro.CREDENCIAIS_INVALIDAS)
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                _estado.value = _estado.value?.copy(carregando = false, erro = LoginErro.CREDENCIAIS_INVALIDAS)
            } catch (erro: Exception) {
                _estado.value = _estado.value?.copy(carregando = false, erro = LoginErro.GENERICO)
            }
        }
    }
}
