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
 * A tela de login do motorista (a primeira que abre no app).
 *
 * Uma `Activity` é uma tela do app. Ela cuida só da parte visual: mostra os campos, reage aos
 * toques e desenha o que o ViewModel mandar. A lógica (conferir campos, falar com o Firebase)
 * fica no [LoginViewModel].
 *
 * O motorista não se cadastra sozinho: o email e a senha são criados pelo gerente no app
 * gerencial. Aqui ele só entra com esses dados.
 */
class LoginActivity : AppCompatActivity() {

    // `by viewModels()` pede ao Android o ViewModel desta tela. Se a tela for recriada (por
    // exemplo, ao girar o celular), o Android devolve o MESMO ViewModel, com os dados preservados.
    private val viewModel: LoginViewModel by viewModels()

    // Referências para os componentes da tela. `lateinit` quer dizer "vou preencher depois"
    // (no onCreate, com findViewById), mas nunca vai ficar nulo quando for usado.
    private lateinit var editEmail: EditText
    private lateinit var editSenha: EditText
    private lateinit var botaoEntrar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Chamado pelo Android quando a tela é criada. É onde a tela é montada.
     *
     * Se o motorista já está logado (o Firebase lembra o login), vai direto para a tela inicial
     * sem nem mostrar o login. Senão: carrega o layout XML (`setContentView`), encontra os
     * componentes pelo id (`findViewById`), diz o que o botão faz ao ser tocado e começa a
     * "observar" o estado do ViewModel — toda vez que o estado mudar, [exibirEstado] é chamada.
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

    /**
     * Desenha o estado atual na tela: mostra a rodinha de carregando (e desliga o botão) enquanto
     * o login está em andamento, mostra a mensagem de erro certa (os textos vêm do `strings.xml`)
     * e, se o login deu certo, abre a tela seguinte.
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

        estado.sucesso?.getConteudoSeNaoTratado()?.let { abrirListaDeViagens() }
    }

    /**
     * Abre a tela inicial do motorista com um `Intent` (o "pedido" ao Android para abrir outra
     * tela) e fecha esta com `finish()`. Assim, apertar "voltar" na tela inicial não volta para o
     * login.
     */
    private fun abrirListaDeViagens() {
        startActivity(Intent(this, MotoristaHomeActivity::class.java))
        finish()
    }
}
