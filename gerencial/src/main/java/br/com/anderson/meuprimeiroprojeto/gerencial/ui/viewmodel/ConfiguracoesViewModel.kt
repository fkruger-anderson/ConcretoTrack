package br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel

import android.util.Log
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import kotlinx.coroutines.launch

/** Estado exibido por [ConfiguracoesFragment][br.com.anderson.meuprimeiroprojeto.gerencial.ui.ConfiguracoesFragment]. */
data class ConfiguracoesUiState(val nomeGerente: String = "")

/** Só busca o nome do gerente logado pra mostrar a saudação. */
class ConfiguracoesViewModel : ViewModel() {

    companion object {
        private const val TAG = "ConfiguracoesViewModel"
    }

    private val _estado = MutableLiveData(ConfiguracoesUiState())
    val estado: LiveData<ConfiguracoesUiState> = _estado

    /** Busca o perfil do gerente logado no Firestore; se falhar, mostra a saudação sem nome. */
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
