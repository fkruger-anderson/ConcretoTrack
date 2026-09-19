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
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroViewModel

/** Cria a conta do gerente no Firebase Auth e salva o perfil (nome) no Firestore. */
class CadastroActivity : AppCompatActivity() {

    private val viewModel: CadastroViewModel by viewModels()

    private lateinit var editNome: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriarConta: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /** Liga os campos da tela, o clique do botão e a observação do estado da ViewModel. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        editNome = findViewById(R.id.editNome)
        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoCriarConta = findViewById(R.id.botaoCriarConta)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoCriarConta.setOnClickListener {
            viewModel.criarConta(
                editNome.text.toString().trim(),
                editEmail.text.toString().trim(),
                editSenha.text.toString()
            )
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Atualiza progresso, erros de campo, erro geral e navega para a Home quando o cadastro dá certo. */
    private fun exibirEstado(estado: CadastroUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoCriarConta.isEnabled = !estado.carregando

        editNome.error = if (estado.erroCampoNome) getString(R.string.cadastro_erro_nome) else null
        editEmail.error = if (estado.erroCampoEmail) getString(R.string.cadastro_erro_email) else null
        editSenha.error = if (estado.erroCampoSenha) getString(R.string.cadastro_erro_senha) else null

        val mensagemErro = when (estado.erroGeral) {
            CadastroErro.EMAIL_EM_USO -> getString(R.string.cadastro_erro_email_em_uso)
            CadastroErro.SENHA_FRACA -> getString(R.string.cadastro_erro_senha)
            CadastroErro.EMAIL_INVALIDO -> getString(R.string.cadastro_erro_email)
            CadastroErro.GENERICO -> getString(R.string.cadastro_erro_generico)
            null -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let {
            startActivity(Intent(this, MainActivity::class.java))
            finish()
        }
    }
}
