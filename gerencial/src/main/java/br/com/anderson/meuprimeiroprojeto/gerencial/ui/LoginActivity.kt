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

/** Tela de login do gerente; se já tem sessão salva no Firebase Auth, pula direto pra Home. */
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoEntrar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Antes de montar a tela, verifica se já existe sessão ativa — se sim, vai direto pra Home
     * sem nem inflar o layout de login.
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
        findViewById<TextView>(R.id.botaoIrParaCadastro).setOnClickListener {
            startActivity(Intent(this, CadastroActivity::class.java))
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Atualiza progresso e mensagem de erro, e abre a Home quando o login dá certo. */
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

    /** Abre a MainActivity e encerra a tela de login (não deve voltar pra ela com o botão "voltar"). */
    private fun abrirHome() {
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
