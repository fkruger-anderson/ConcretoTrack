package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.LoginErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.LoginUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.LoginViewModel

/**
 * A tela de login do gerente (a primeira que abre no app gerencial).
 *
 * Uma `Activity` é uma tela do app. Ela só cuida da parte visual; a lógica fica no
 * [LoginViewModel]. Se o gerente já estiver logado (o Firebase lembra), a tela pula direto para a
 * tela inicial.
 */
class LoginActivity : AppCompatActivity() {

    // `by viewModels()` pede ao Android o ViewModel desta tela; ao girar o celular, o Android
    // devolve o MESMO ViewModel, com os dados preservados.
    private val viewModel: LoginViewModel by viewModels()

    // Componentes da tela. `lateinit` = serão preenchidos no onCreate, com findViewById.
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoEntrar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Chamado pelo Android quando a tela é criada.
     *
     * Primeiro vê se já há alguém logado; se sim, abre a tela inicial sem nem montar o login. Senão,
     * carrega o layout XML, encontra os componentes pelo id, liga o botão Entrar e começa a observar
     * o estado do ViewModel (toda mudança chama [exibirEstado]).
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.jaEstaLogado()) {
            abrirHome()
            return
        }

        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoEntrar = findViewById(R.id.botaoEntrar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoEntrar.setOnClickListener {
            viewModel.entrar(editEmail.text.toString().trim(), editSenha.text.toString())
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Desenha o estado atual: rodinha de carregando (e botão desligado) durante o login, a mensagem
     * de erro certa (textos do `strings.xml`) e, se deu certo, abre a tela inicial.
     */
    private fun exibirEstado(estado: LoginUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoEntrar.isEnabled = !estado.carregando

        val mensagemErro = when (estado.erro) {
            LoginErro.CAMPOS_VAZIOS -> getString(R.string.login_erro_campos_vazios)
            LoginErro.CREDENCIAIS_INVALIDAS -> getString(R.string.login_erro_credenciais)
            LoginErro.GENERICO -> getString(R.string.login_erro_generico)
            null -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let { abrirHome() }
    }

    /**
     * Abre a [MainActivity] com um `Intent` e fecha o login com `finish()`, para o botão "voltar"
     * não retornar ao login.
     */
    private fun abrirHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
