package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroMotoristaErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroMotoristaUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroMotoristaViewModel

/**
 * A tela de cadastro de motorista (nome, CPF, email e senha). Cria o login que o motorista vai usar
 * no app dele e o perfil. Aberta pela aba Configurações.
 */
class CadastroMotoristaActivity : AppCompatActivity() {

    private val viewModel: CadastroMotoristaViewModel by viewModels()

    // Componentes da tela, preenchidos no onCreate com findViewById.
    private lateinit var editNome: EditText
    private lateinit var editCpf: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Chamado pelo Android quando a tela é criada: liga os campos, a máscara de CPF ([MascaraCpf]),
     * o botão Criar (que entrega os textos ao ViewModel) e a observação do estado.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_motorista)

        editNome = findViewById(R.id.editNome)
        editCpf = findViewById(R.id.editCpf)
        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoCriar = findViewById(R.id.botaoCriar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        editCpf.addTextChangedListener(MascaraCpf(editCpf))
        botaoCriar.setOnClickListener {
            viewModel.criar(
                editNome.text.toString(),
                editCpf.text.toString(),
                editEmail.text.toString(),
                editSenha.text.toString()
            )
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Desenha o estado atual: rodinha de carregando, erros em cada campo, a mensagem geral de erro
     * e, se deu certo, um Toast e o fechamento da tela.
     */
    private fun exibirEstado(estado: CadastroMotoristaUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoCriar.isEnabled = !estado.carregando

        editNome.error = if (estado.erroCampoNome) getString(R.string.cadastro_motorista_erro_nome) else null
        editCpf.error = if (estado.erroCampoCpf) getString(R.string.cadastro_motorista_erro_cpf) else null
        editEmail.error = if (estado.erroCampoEmail) getString(R.string.cadastro_motorista_erro_email) else null
        editSenha.error = if (estado.erroCampoSenha) getString(R.string.cadastro_motorista_erro_senha) else null

        val mensagemErro = when (estado.erroGeral) {
            CadastroMotoristaErro.EMAIL_EM_USO -> getString(R.string.cadastro_motorista_erro_email_em_uso)
            CadastroMotoristaErro.SENHA_FRACA -> getString(R.string.cadastro_motorista_erro_senha)
            CadastroMotoristaErro.EMAIL_INVALIDO -> getString(R.string.cadastro_motorista_erro_email)
            CadastroMotoristaErro.GENERICO -> getString(R.string.cadastro_motorista_erro_generico)
            null -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(this, R.string.cadastro_motorista_sucesso, Toast.LENGTH_LONG).show()
            finish()
        }
    }

    /**
     * Coloca a máscara de CPF (000.000.000-00) enquanto o gerente digita. Aceita só CPF (no máximo
     * 11 números), porque motorista é sempre pessoa física.
     *
     * Um `TextWatcher` é um "vigia" do campo de texto: o Android chama as funções dele antes,
     * durante e depois de cada mudança.
     */
    private class MascaraCpf(private val campo: EditText) : TextWatcher {
        // Evita que a própria troca de texto feita aqui dispare o vigia de novo (um laço sem fim).
        private var atualizandoTexto = false

        // Exigidos pela interface TextWatcher, mas não são usados aqui.
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        /**
         * Chamado depois de cada mudança no texto: fica só com os números (no máximo 11), refaz a
         * máscara e, se o texto mudou, troca o conteúdo do campo e põe o cursor no fim.
         */
        override fun afterTextChanged(s: Editable?) {
            if (atualizandoTexto || s == null) return
            val digitos = DocumentoUtils.apenasDigitos(s.toString()).take(11)
            val formatado = DocumentoUtils.formatar(digitos)
            if (formatado == s.toString()) return

            atualizandoTexto = true
            campo.setText(formatado)
            campo.setSelection(formatado.length)
            atualizandoTexto = false
        }
    }
}
