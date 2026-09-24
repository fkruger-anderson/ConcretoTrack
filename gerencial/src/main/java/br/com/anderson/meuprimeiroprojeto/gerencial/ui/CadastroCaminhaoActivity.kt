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
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroCaminhaoErro
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroCaminhaoUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CadastroCaminhaoViewModel

/**
 * A tela de cadastro de caminhão (placa, marca, modelo e ano). Os caminhões cadastrados aparecem
 * depois na tela de atribuir viagem. Aberta pela aba Configurações.
 */
class CadastroCaminhaoActivity : AppCompatActivity() {

    private val viewModel: CadastroCaminhaoViewModel by viewModels()

    // Componentes da tela, preenchidos no onCreate com findViewById.
    private lateinit var editPlaca: EditText
    private lateinit var editMarca: EditText
    private lateinit var editModelo: EditText
    private lateinit var editAno: EditText
    private lateinit var botaoCriar: Button
    private lateinit var progresso: ProgressBar
    private lateinit var textErro: TextView

    /**
     * Chamado pelo Android quando a tela é criada: liga os campos, o botão Criar (que entrega os
     * textos digitados ao ViewModel) e a observação do estado.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_cadastro_caminhao)

        editPlaca = findViewById(R.id.editPlaca)
        editMarca = findViewById(R.id.editMarca)
        editModelo = findViewById(R.id.editModelo)
        editAno = findViewById(R.id.editAno)
        botaoCriar = findViewById(R.id.botaoCriar)
        progresso = findViewById(R.id.progresso)
        textErro = findViewById(R.id.textErro)

        botaoCriar.setOnClickListener {
            viewModel.criar(
                editPlaca.text.toString(),
                editMarca.text.toString(),
                editModelo.text.toString(),
                editAno.text.toString()
            )
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Desenha o estado atual: rodinha de carregando, erro nos campos placa e ano (o `.error` de um
     * campo mostra um ícone vermelho com a mensagem), a mensagem geral de erro e, se deu certo, um
     * Toast e o fechamento da tela.
     */
    private fun exibirEstado(estado: CadastroCaminhaoUiState) {
        progresso.visibility = if (estado.carregando) View.VISIBLE else View.GONE
        botaoCriar.isEnabled = !estado.carregando

        editPlaca.error = if (estado.erroCampoPlaca) getString(R.string.cadastro_caminhao_erro_placa) else null
        editAno.error = if (estado.erroCampoAno) getString(R.string.cadastro_caminhao_erro_ano) else null

        val mensagemErro = when {
            estado.erroMarcaModelo -> getString(R.string.cadastro_caminhao_erro_marca_modelo)
            estado.erroGeral == CadastroCaminhaoErro.PLACA_DUPLICADA ->
                getString(R.string.cadastro_caminhao_erro_placa_duplicada)
            estado.erroGeral == CadastroCaminhaoErro.GENERICO -> getString(R.string.cadastro_caminhao_erro_generico)
            else -> null
        }
        textErro.visibility = if (mensagemErro != null) View.VISIBLE else View.GONE
        if (mensagemErro != null) textErro.text = mensagemErro

        estado.sucesso?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(this, R.string.cadastro_caminhao_sucesso, Toast.LENGTH_LONG).show()
            finish()
        }
    }
}
