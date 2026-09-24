package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroGerenteErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroGerenteUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroGerenteViewModel

/**
 * A tela de cadastro de outro gerente (nome, email e senha), usada por um gerente que já está
 * logado. Aberta pela aba Configurações. Quem cadastra continua logado depois.
 */
class CadastroGerenteActivity : AppCompatActivity() {

    private val viewModel: CadastroGerenteViewModel by viewModels()

    // Componentes da tela, preenchidos no onCreate com findViewById.
    private lateinit var editNome: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Chamado pelo Android quando a tela é criada: liga os campos, o botão Criar (que entrega os
     * textos digitados ao ViewModel) e a observação do estado.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_gerente)

        editNome = findViewById(R.id.editNome)
        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoCriar = findViewById(R.id.botaoCriar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoCriar.setOnClickListener {
            viewModel.criar(editNome.text.toString(), editEmail.text.toString(), editSenha.text.toString())
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Desenha o estado atual: rodinha de carregando, erros em cada campo, a mensagem geral de erro
     * (email já usado, senha fraca...) e, se deu certo, um Toast e o fechamento da tela.
     */
    private fun exibirEstado(estado: CadastroGerenteUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoCriar.isEnabled = !estado.carregando

        editNome.error = if (estado.erroCampoNome) getString(R.string.cadastro_erro_nome) else null
        editEmail.error = if (estado.erroCampoEmail) getString(R.string.cadastro_erro_email) else null
        editSenha.error = if (estado.erroCampoSenha) getString(R.string.cadastro_erro_senha) else null

        val mensagemErro = when (estado.erroGeral) {
            CadastroGerenteErro.EMAIL_EM_USO -> getString(R.string.cadastro_erro_email_em_uso)
            CadastroGerenteErro.SENHA_FRACA -> getString(R.string.cadastro_erro_senha)
            CadastroGerenteErro.EMAIL_INVALIDO -> getString(R.string.cadastro_erro_email)
            CadastroGerenteErro.GENERICO -> getString(R.string.cadastro_erro_generico)
            null -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(this, R.string.cadastro_gerente_sucesso, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
