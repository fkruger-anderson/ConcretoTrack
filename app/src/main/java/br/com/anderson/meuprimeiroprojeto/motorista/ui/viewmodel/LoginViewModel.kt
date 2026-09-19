package br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch

/** Os três jeitos de dar errado no login — a Activity escolhe o texto certo pra cada um. */
enum class LoginErro { CAMPOS_VAZIOS, CREDENCIAIS_INVALIDAS, GENERICO }

/** Estado exibido por [LoginActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.LoginActivity]. */
data class LoginUiState(
    val carregando: Boolean = false,
    val erro: LoginErro? = null,
    val sucesso: Evento<Unit>? = null
)

/**
 * Autentica o motorista e guarda nome/cpf em cache local ([SessaoMotorista]) pra não precisar
 * buscar no Firestore em toda tela. É [AndroidViewModel] pelo mesmo motivo de
 * [ViagemEmAndamentoViewModel][br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagemEmAndamentoViewModel]:
 * salvar esse cache exige um `Context`, e a `Application` é a forma seguro de ter um aqui dentro.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    private val _estado = MutableLiveData(LoginUiState())
    val estado: LiveData<LoginUiState> = _estado

    /** O Firebase Auth já lembra a sessão sozinho; se já tem usuário, a Activity pula o login. */
    fun jaEstaLogado(): Boolean = AutenticacaoRepositorio.usuarioAtual != null

    /**
     * Valida que os campos não estão vazios, autentica no Firebase Auth e salva nome/cpf em
     * [SessaoMotorista] para uso local nas próximas telas.
     */
    fun entrar(email: String, senha: String) {
        if (email.isEmpty() || senha.isEmpty()) {
            _estado.value = _estado.value?.copy(erro = LoginErro.CAMPOS_VAZIOS)
            return
        }

        _estado.value = _estado.value?.copy(carregando = true, erro = null)
        viewModelScope.launch {
            try {
                val usuario = AutenticacaoRepositorio.entrar(email, senha)
                val perfil = AutenticacaoRepositorio.buscarPerfil(usuario.uid)
                if (perfil != null) {
                    SessaoMotorista.salvar(getApplication(), perfil.nome, perfil.cpf)
                }
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
