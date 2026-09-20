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
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasViewModel

/** Lista única das viagens em aberto do motorista (atribuídas ou em rota). */
class ViagensAbertasActivity : AppCompatActivity() {

    private val viewModel: ViagensAbertasViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_viagens_abertas)

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

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Recarrega a lista sempre que a tela volta a ficar visível (ex.: após voltar da tela de detalhes). */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    private fun exibirEstado(estado: ViagensAbertasUiState) {
        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.viagens_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAbertaAdapter(estado.viagens) { item ->
                val intent = Intent(this, DetalhesViagemActivity::class.java)
                    .putExtra(DetalhesViagemActivity.EXTRA_VIAGEM_ID, item.viagemId)
                startActivity(intent)
            }

            val listaVazia = estado.viagens.isEmpty()
            textListaVazia.text = getString(R.string.viagens_lista_vazia)
            textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
            recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
        }
    }
}
