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

/**
 * Os tipos de erro possíveis no login. A tela ([LoginActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.LoginActivity])
 * escolhe o texto certo para cada um no `strings.xml`.
 */
enum class LoginErro { CAMPOS_VAZIOS, CREDENCIAIS_INVALIDAS, GENERICO }

/**
 * Tudo o que a tela de login precisa desenhar, num objeto só (o "estado da tela").
 *
 * A tela não guarda nada por conta própria: ela recebe um destes do ViewModel e se desenha de
 * acordo. Cada mudança gera um objeto novo (com `copy`), e a tela é redesenhada.
 */
data class LoginUiState(
    /** `true` enquanto o login está em andamento (mostra a rodinha e desliga o botão). */
    val carregando: Boolean = false,
    /** O erro a mostrar, ou `null` se não houver erro. */
    val erro: LoginErro? = null,
    /** Aviso de "login deu certo", tratado uma vez só (ver [Evento]). */
    val sucesso: Evento<Unit>? = null
)

/**
 * O "cérebro" da tela de login do motorista.
 *
 * Um ViewModel guarda os dados e a lógica de uma tela, separado da parte visual. Ele sobrevive
 * quando a tela é recriada (por exemplo, ao girar o celular), então um login em andamento não se
 * perde. A tela observa o [estado] e se redesenha sempre que ele muda.
 *
 * Este é um `AndroidViewModel` (e não um `ViewModel` comum) porque precisa do `Context` da
 * aplicação para guardar o nome do motorista nas SharedPreferences ([SessaoMotorista]). O
 * `Application` é o contexto do app inteiro, seguro de guardar num ViewModel.
 */
class LoginViewModel(application: Application) : AndroidViewModel(application) {

    // Padrão usado em todos os ViewModels do projeto: `_estado` (com sublinhado) é a versão que
    // o ViewModel pode mudar (`MutableLiveData`); `estado` é a versão só de leitura que a tela
    // enxerga. Assim, só o ViewModel consegue alterar o estado.
    private val _estado = MutableLiveData(LoginUiState())
    /**
     * O estado atual da tela. `LiveData` é uma "caixinha observável": quem observa é avisado toda
     * vez que o valor dentro dela muda.
     */
    val estado: LiveData<LoginUiState> = _estado

    /**
     * Diz se já existe alguém logado. O Firebase Auth lembra o login sozinho entre aberturas do
     * app; se já tem usuário, a tela pula o login.
     */
    fun jaEstaLogado(): Boolean = AutenticacaoRepositorio.usuarioAtual != null

    /**
     * Tenta fazer o login.
     *
     * 1. Se email ou senha estiverem vazios, mostra o erro e para (nem chega a usar a internet).
     * 2. Liga o "carregando" e, dentro de uma coroutine (`viewModelScope.launch`), faz o login no
     *    Firebase e busca o perfil do motorista.
     * 3. Guarda o nome no celular ([SessaoMotorista]) para as próximas telas.
     * 4. Se der certo, publica o aviso de sucesso. Se der errado, publica o tipo de erro.
     *
     * `viewModelScope` é um espaço de coroutines ligado ao ViewModel: se o ViewModel for destruído
     * (a tela fechou de vez), o que estava rodando ali é cancelado sozinho.
     *
     * O `try/catch` captura os erros: cada `catch` pega um tipo específico. Email inexistente ou
     * senha errada viram a mesma mensagem, para não revelar qual dos dois estava errado.
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
                    SessaoMotorista.salvar(getApplication(), perfil.nome)
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
