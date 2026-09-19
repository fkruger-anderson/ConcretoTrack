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

/** Cadastro de motorista pelo gerente — cria o login (email/senha) e o perfil dele. */
class CadastroMotoristaActivity : AppCompatActivity() {

    private val viewModel: CadastroMotoristaViewModel by viewModels()

    private lateinit var editNome: EditText
    private lateinit var editCpf: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /** Liga os campos da tela (inclusive a máscara de CPF), o clique do botão e a observação do estado da ViewModel. */
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

    /** Atualiza progresso, erros de campo, erro geral e fecha a tela com um toast quando o cadastro dá certo. */
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

    /** Só aceita CPF aqui (motorista é sempre pessoa física), reformata como 000.000.000-00. */
    private class MascaraCpf(private val campo: EditText) : TextWatcher {
        private var atualizandoTexto = false

        // Exigidos pela interface TextWatcher, mas não são usados aqui.
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

        /** Reformata o texto a cada digitação; a flag evita loop infinito (setText dispara este mesmo callback). */
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
