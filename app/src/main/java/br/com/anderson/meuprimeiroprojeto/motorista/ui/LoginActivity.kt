package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
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
    private lateinit var botaoMostrarSenha: ImageButton

    // Se a senha está aparecendo em texto normal (true) ou escondida com bolinhas (false).
    // É salvo no Bundle em onSaveInstanceState para não se perder ao girar o celular.
    private var senhaVisivel = false

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
        botaoMostrarSenha = findViewById(R.id.botaoMostrarSenha)

        senhaVisivel = savedInstanceState?.getBoolean(CHAVE_SENHA_VISIVEL) ?: false
        aplicarVisibilidadeDaSenha()
        botaoMostrarSenha.setOnClickListener {
            senhaVisivel = !senhaVisivel
            aplicarVisibilidadeDaSenha()
        }

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
        // Enquanto carrega, o texto do botão some e a rodinha aparece no lugar dele. O fundo
        // continua vermelho mesmo desabilitado (backgroundTint fixo no layout).
        botaoEntrar.text = if (estado.carregando) "" else getString(R.string.login_botao_entrar)
        botaoEntrar.contentDescription =
            if (estado.carregando) getString(R.string.login_entrando) else null

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

    /**
     * Guarda no Bundle se a senha estava visível, para restaurar quando a tela for recriada
     * (por exemplo, ao girar o celular).
     */
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(CHAVE_SENHA_VISIVEL, senhaVisivel)
    }

    /**
     * Mostra ou esconde a senha conforme [senhaVisivel] e troca o ícone do olho. O cursor é
     * mantido na mesma posição, senão ele pularia para o início do campo a cada toque.
     */
    private fun aplicarVisibilidadeDaSenha() {
        val posicaoCursor = editSenha.selectionEnd
        editSenha.transformationMethod = if (senhaVisivel) {
            HideReturnsTransformationMethod.getInstance()
        } else {
            PasswordTransformationMethod.getInstance()
        }
        if (posicaoCursor >= 0) editSenha.setSelection(posicaoCursor)

        botaoMostrarSenha.setImageResource(
            if (senhaVisivel) R.drawable.ic_olho_fechado else R.drawable.ic_olho
        )
        botaoMostrarSenha.contentDescription = getString(
            if (senhaVisivel) R.string.login_ocultar_senha else R.string.login_mostrar_senha
        )
    }

    companion object {
        private const val CHAVE_SENHA_VISIVEL = "senha_visivel"
    }
}
