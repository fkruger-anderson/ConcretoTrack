package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.MinhasViagensUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.MinhasViagensViewModel

/** Lista as viagens que o gerente atribuiu a este motorista e que ainda não foram iniciadas. */
class MinhasViagensActivity : AppCompatActivity() {

    private val viewModel: MinhasViagensViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView
    private lateinit var botaoViagemEmAndamento: Button

    /** Monta a tela (saudação, botão de sair, RecyclerView) e começa a observar o estado da ViewModel. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_minhas_viagens)

        findViewById<TextView>(R.id.textBoasVindas).text =
            getString(R.string.viagens_boas_vindas, SessaoMotorista.nomeMotorista(this))

        findViewById<Button>(R.id.botaoSair).setOnClickListener {
            AutenticacaoRepositorio.sair()
            SessaoMotorista.sair(this)
            startActivity(Intent(this, LoginActivity::class.java))
            finish()
        }

        recyclerViagens = findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(this)
        textListaVazia = findViewById(R.id.textListaVazia)
        botaoViagemEmAndamento = findViewById(R.id.botaoViagemEmAndamento)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Recarrega a lista sempre que a tela volta a ficar visível (ex.: após finalizar uma viagem). */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    /**
     * Desenha a lista de viagens atribuídas (ou o aviso de lista vazia/erro) e o botão de
     * "viagem em andamento", que só aparece se o motorista tiver deixado uma viagem em rota
     * sem finalizar.
     */
    private fun exibirEstado(estado: MinhasViagensUiState) {
        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.viagens_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAtribuidaAdapter(estado.viagens) { item ->
                val intent = Intent(this, IniciarTransitoActivity::class.java)
                    .putExtra(IniciarTransitoActivity.EXTRA_VIAGEM_ID, item.viagemId)
                startActivity(intent)
            }

            val listaVazia = estado.viagens.isEmpty()
            textListaVazia.text = getString(R.string.viagens_lista_vazia)
            textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
            recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
        }

        val resumo = estado.viagemEmAndamento
        if (resumo == null) {
            botaoViagemEmAndamento.visibility = View.GONE
        } else {
            botaoViagemEmAndamento.text = getString(R.string.viagens_em_andamento, resumo.descricaoPedido)
            botaoViagemEmAndamento.visibility = View.VISIBLE
            botaoViagemEmAndamento.setOnClickListener {
                val intent = Intent(this, ViagemEmAndamentoActivity::class.java)
                    .putExtra(ViagemEmAndamentoActivity.EXTRA_VIAGEM_ID, resumo.viagemId)
                startActivity(intent)
            }
        }
    }
}
