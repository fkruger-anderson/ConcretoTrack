package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAtribuidaComPedido
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import kotlinx.coroutines.launch

/** Lista as viagens que o gerente atribuiu a este motorista e que ainda não foram iniciadas. */
class MinhasViagensActivity : AppCompatActivity() {

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView
    private lateinit var botaoViagemEmAndamento: Button

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
    }

    override fun onResume() {
        super.onResume()
        carregarViagensAtribuidas()
        verificarViagemEmAndamento()
    }

    /** Se o motorista saiu da tela de rastreamento sem finalizar, mostra um jeito de voltar a ela. */
    private fun verificarViagemEmAndamento() {
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        lifecycleScope.launch {
            val encontrada = try {
                ViagemRepositorio.buscarEmRotaDoMotorista(uid)
            } catch (erro: Exception) {
                null
            }
            if (encontrada == null) {
                botaoViagemEmAndamento.visibility = View.GONE
                return@launch
            }

            val (viagemId, viagem) = encontrada
            val pedido = try {
                PedidoRepositorio.buscarPorId(viagem.pedidoId)
            } catch (erro: Exception) {
                null
            }
            botaoViagemEmAndamento.text = getString(
                R.string.viagens_em_andamento, pedido?.destinatarioNome ?: viagem.pedidoId.toString()
            )
            botaoViagemEmAndamento.visibility = View.VISIBLE
            botaoViagemEmAndamento.setOnClickListener {
                val intent = Intent(this@MinhasViagensActivity, ViagemEmAndamentoActivity::class.java)
                    .putExtra(ViagemEmAndamentoActivity.EXTRA_VIAGEM_ID, viagemId)
                startActivity(intent)
            }
        }
    }

    private fun carregarViagensAtribuidas() {
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        lifecycleScope.launch {
            try {
                val atribuidas = ViagemRepositorio.buscarAtribuidasDoMotorista(uid)
                val comPedido = atribuidas.map { (viagemId, viagem) ->
                    val pedido = try {
                        PedidoRepositorio.buscarPorId(viagem.pedidoId)
                    } catch (erro: Exception) {
                        null
                    }
                    ViagemAtribuidaComPedido(viagemId, viagem, pedido)
                }

                recyclerViagens.adapter = ViagemAtribuidaAdapter(comPedido) { item ->
                    val intent = Intent(this@MinhasViagensActivity, IniciarTransitoActivity::class.java)
                        .putExtra(IniciarTransitoActivity.EXTRA_VIAGEM_ID, item.viagemId)
                    startActivity(intent)
                }

                val listaVazia = comPedido.isEmpty()
                textListaVazia.text = getString(R.string.viagens_lista_vazia)
                textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
                recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
            } catch (erro: Exception) {
                textListaVazia.text = getString(R.string.viagens_erro_carregar)
                textListaVazia.visibility = View.VISIBLE
                recyclerViagens.visibility = View.GONE
            }
        }
    }
}
