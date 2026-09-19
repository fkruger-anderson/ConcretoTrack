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
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.MotoristaRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.DocumentoUtils
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/** Cadastro de motorista pelo gerente — cria o login (email/senha) e o perfil dele. */
class CadastroMotoristaActivity : AppCompatActivity() {

    private lateinit var editNome: EditText
    private lateinit var editCpf: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

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
        botaoCriar.setOnClickListener { tentarCriar() }
    }

    private fun tentarCriar() {
        val nome = editNome.text.toString().trim()
        val cpf = DocumentoUtils.apenasDigitos(editCpf.text.toString())
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()

        if (nome.isEmpty()) {
            editNome.error = getString(R.string.cadastro_motorista_erro_nome)
            return
        }
        if (cpf.length != 11 || !DocumentoUtils.documentoValido(cpf)) {
            editCpf.error = getString(R.string.cadastro_motorista_erro_cpf)
            return
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.error = getString(R.string.cadastro_motorista_erro_email)
            return
        }
        if (senha.length < 6) {
            editSenha.error = getString(R.string.cadastro_motorista_erro_senha)
            return
        }

        mostrarCarregando(true)
        lifecycleScope.launch {
            try {
                MotoristaRepositorio.cadastrar(this@CadastroMotoristaActivity, nome, cpf, email, senha)
                Toast.makeText(
                    this@CadastroMotoristaActivity, R.string.cadastro_motorista_sucesso, Toast.LENGTH_LONG
                ).show()
                finish()
            } catch (erro: FirebaseAuthUserCollisionException) {
                mostrarErro(getString(R.string.cadastro_motorista_erro_email_em_uso))
            } catch (erro: FirebaseAuthWeakPasswordException) {
                mostrarErro(getString(R.string.cadastro_motorista_erro_senha))
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                mostrarErro(getString(R.string.cadastro_motorista_erro_email))
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.cadastro_motorista_erro_generico))
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        botaoCriar.isEnabled = !carregando
        if (carregando) textErro.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }

    /** Só aceita CPF aqui (motorista é sempre pessoa física), reformata como 000.000.000-00. */
    private class MascaraCpf(private val campo: EditText) : TextWatcher {
        private var atualizandoTexto = false

        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

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
