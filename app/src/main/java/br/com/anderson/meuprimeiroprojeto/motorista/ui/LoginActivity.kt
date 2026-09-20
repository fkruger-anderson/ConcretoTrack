package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.LoginErro
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.LoginUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.LoginViewModel

/**
 * Tela de login do motorista. O motorista não se cadastra sozinho — email e senha são
 * criados pelo gerente no app gerencial; aqui ele só entra com essas credenciais.
 */
class LoginActivity : AppCompatActivity() {

    private val viewModel: LoginViewModel by viewModels()

    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoEntrar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Se já existe uma sessão do Firebase Auth ativa, pula direto para a lista de viagens
     * sem mostrar a tela de login de novo.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (viewModel.jaEstaLogado()) {
            abrirListaDeViagens()
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

    /** Mostra progresso/erro conforme o estado da ViewModel e navega ao ter sucesso no login. */
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

        estado.sucesso?.getConteudoSeNaoTratado()?.let { abrirListaDeViagens() }
    }

    /** Abre a lista de viagens e encerra o login, para que o botão "voltar" não retorne pra ele. */
    private fun abrirListaDeViagens() {
        startActivity(Intent(this, ViagensAbertasActivity::class.java))
        finish()
    }
}
