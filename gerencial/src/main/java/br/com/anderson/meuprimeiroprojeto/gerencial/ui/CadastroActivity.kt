package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import kotlinx.coroutines.launch

/** Cria a conta do gerente no Firebase Auth e salva o perfil (nome) no Firestore. */
class CadastroActivity : AppCompatActivity() {

    private lateinit var editNome: EditText
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoCriarConta: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro)

        editNome = findViewById(R.id.editNome)
        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoCriarConta = findViewById(R.id.botaoCriarConta)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoCriarConta.setOnClickListener { tentarCriarConta() }
    }

    private fun tentarCriarConta() {
        val nome = editNome.text.toString().trim()
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()

        if (nome.isEmpty()) {
            editNome.error = getString(R.string.cadastro_erro_nome)
            return
        }
        if (email.isEmpty() || !android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
            editEmail.error = getString(R.string.cadastro_erro_email)
            return
        }
        if (senha.length < 6) {
            editSenha.error = getString(R.string.cadastro_erro_senha)
            return
        }

        mostrarCarregando(true)
        lifecycleScope.launch {
            try {
                AutenticacaoRepositorio.cadastrar(nome, email, senha)
                startActivity(Intent(this@CadastroActivity, MainActivity::class.java))
                finish()
            } catch (erro: FirebaseAuthUserCollisionException) {
                mostrarErro(getString(R.string.cadastro_erro_email_em_uso))
            } catch (erro: FirebaseAuthWeakPasswordException) {
                mostrarErro(getString(R.string.cadastro_erro_senha))
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                mostrarErro(getString(R.string.cadastro_erro_email))
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.cadastro_erro_generico))
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        botaoCriarConta.isEnabled = !carregando
        if (carregando) textErro.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }
}
