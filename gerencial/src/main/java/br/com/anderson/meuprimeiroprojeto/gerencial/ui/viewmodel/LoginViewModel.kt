package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch

/**
 * Os tipos de erro possíveis no login. A tela ([LoginActivity][br.com.anderson.meuprimeiroprojeto.gerencial.ui.LoginActivity])
 * escolhe o texto certo para cada um no `strings.xml`.
 */
enum class LoginErro { CAMPOS_VAZIOS, CREDENCIAIS_INVALIDAS, GENERICO }

/** Tudo o que a tela de login precisa desenhar (o "estado da tela"). */
data class LoginUiState(
    /** `true` enquanto o login está em andamento. */
    val carregando: Boolean = false,
    /** O erro a mostrar, ou `null`. */
    val erro: LoginErro? = null,
    /** Aviso de "login deu certo", tratado uma vez só. */
    val sucesso: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de login do gerente.
 *
 * Um ViewModel guarda os dados e a lógica de uma tela, separado da parte visual, e sobrevive
 * quando a tela é recriada (ao girar o celular). A tela observa o [estado] e se redesenha quando
 * ele muda.
 *
 * É um `ViewModel` comum: não guarda nada no celular, então não precisa de `Context`.
 */
class LoginViewModel : ViewModel() {

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(LoginUiState())
    /** O estado atual da tela. `LiveData` avisa quem está observando toda vez que o valor muda. */
    val estado: LiveData<LoginUiState> = _estado

    /** Diz se já há um gerente logado (o Firebase lembra o login); se sim, a tela pula o login. */
    fun jaEstaLogado(): Boolean = AutenticacaoRepositorio.usuarioAtual != null

    /**
     * Tenta fazer o login. Se email ou senha estiverem vazios, mostra o erro sem nem usar a
     * internet. Senão, faz o login dentro de uma coroutine (`viewModelScope.launch`, que é
     * cancelada sozinha se o ViewModel for destruído) e publica o sucesso ou o erro.
     *
     * Email inexistente e senha errada viram o mesmo erro, para não revelar qual dos dois estava
     * errado (quem tenta adivinhar senhas não ganha pistas).
     */
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
