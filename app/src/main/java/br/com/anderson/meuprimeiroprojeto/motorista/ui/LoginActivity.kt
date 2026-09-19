package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoEntrar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // O Firebase Auth já lembra a sessão sozinho; se já tem usuário, pula pra lista.
        if (AutenticacaoRepositorio.usuarioAtual != null) {
            abrirMinhasViagens()
            return
        }

        setContentView(R.layout.activity_login)

        editEmail = findViewById(R.id.editEmail)
        editSenha = findViewById(R.id.editSenha)
        botaoEntrar = findViewById(R.id.botaoEntrar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoEntrar.setOnClickListener { tentarEntrar() }
    }

    private fun tentarEntrar() {
        val email = editEmail.text.toString().trim()
        val senha = editSenha.text.toString()

        if (email.isEmpty() || senha.isEmpty()) {
            mostrarErro(getString(R.string.login_erro_campos_vazios))
            return
        }

        mostrarCarregando(true)
        lifecycleScope.launch {
            try {
                val usuario = AutenticacaoRepositorio.entrar(email, senha)
                val perfil = AutenticacaoRepositorio.buscarPerfil(usuario.uid)
                if (perfil != null) {
                    SessaoMotorista.salvar(this@LoginActivity, perfil.nome, perfil.cpf)
                }
                abrirMinhasViagens()
            } catch (erro: FirebaseAuthInvalidUserException) {
                mostrarErro(getString(R.string.login_erro_credenciais))
            } catch (erro: FirebaseAuthInvalidCredentialsException) {
                mostrarErro(getString(R.string.login_erro_credenciais))
            } catch (erro: Exception) {
                mostrarErro(getString(R.string.login_erro_generico))
            } finally {
                mostrarCarregando(false)
            }
        }
    }

    private fun mostrarCarregando(carregando: Boolean) {
        progresso.visibility = if (carregando) View.VISIBLE else View.GONE
        botaoEntrar.isEnabled = !carregando
        if (carregando) textErro.visibility = View.GONE
    }

    private fun mostrarErro(mensagem: String) {
        textErro.text = mensagem
        textErro.visibility = View.VISIBLE
    }

    private fun abrirMinhasViagens() {
        startActivity(Intent(this, MinhasViagensActivity::class.java))
        finish()
    }
}
