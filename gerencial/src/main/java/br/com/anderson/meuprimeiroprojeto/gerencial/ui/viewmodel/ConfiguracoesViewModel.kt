package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import kotlinx.coroutines.launch

/**
 * O estado da aba Configurações ([ConfiguracoesFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.ConfiguracoesFragment]):
 * só o nome do gerente, para a saudação.
 */
data class ConfiguracoesUiState(val nomeGerente: String = "")

/** O "cérebro" da aba Configurações: só busca o nome do gerente logado para mostrar "Olá, fulano". */
class ConfiguracoesViewModel : ViewModel() {

    companion object {
        // Etiqueta das mensagens desta classe no Logcat.
        private const val TAG = "ConfiguracoesViewModel"
    }

    // `_estado` é a versão que o ViewModel pode mudar; `estado` é a versão só de leitura da tela.
    private val _estado = MutableLiveData(ConfiguracoesUiState())
    /** O estado atual da aba; a tela é avisada toda vez que ele muda. */
    val estado: LiveData<ConfiguracoesUiState> = _estado

    /**
     * Busca o perfil do gerente logado no Firestore. Se ninguém estiver logado, não faz nada. Se a
     * busca falhar, registra no Logcat e mostra a saudação sem o nome.
     */
    fun carregar() {
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModelScope.launch {
            val perfil = try {
                AutenticacaoRepositorio.buscarPerfil(uid)
            } catch (erro: Exception) {
                Log.w(TAG, "Falha ao buscar perfil do gerente $uid", erro)
                null
            }
            _estado.value = ConfiguracoesUiState(nomeGerente = perfil?.nome ?: "")
        }
    }
}
