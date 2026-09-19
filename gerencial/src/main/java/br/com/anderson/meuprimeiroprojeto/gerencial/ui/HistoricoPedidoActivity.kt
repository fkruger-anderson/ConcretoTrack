package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.HistoricoPedidoUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.HistoricoPedidoViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ResultadoCancelamento

/** Histórico de viagens de um pedido: quem levou, quanto, quando, e como terminou. */
class HistoricoPedidoActivity : AppCompatActivity() {

    companion object {
        /** Chave do Intent extra: id do pedido cujo histórico está sendo mostrado. */
        const val EXTRA_PEDIDO_ID = "pedido_id"
    }

    private val viewModel: HistoricoPedidoViewModel by viewModels()

    private var pedidoId: Int = -1
    private lateinit var textNumero: TextView
    private lateinit var textProgresso: TextView
    private lateinit var botaoAtribuirViagem: Button
    private lateinit var textListaVazia: TextView
    private lateinit var recyclerViagens: RecyclerView

    /** Lê o pedido recebido por Intent, liga as Views e o botão de atribuir nova viagem. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_historico_pedido)

        pedidoId = intent.getIntExtra(EXTRA_PEDIDO_ID, -1)
        if (pedidoId <= 0) {
            finish()
            return
        }

        textNumero = findViewById(R.id.textNumero)
        textProgresso = findViewById(R.id.textProgresso)
        botaoAtribuirViagem = findViewById(R.id.botaoAtribuirViagem)
        textListaVazia = findViewById(R.id.textListaVazia)
        recyclerViagens = findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(this)

        botaoAtribuirViagem.setOnClickListener {
            val intent = Intent(this, AtribuirViagemActivity::class.java)
                .putExtra(AtribuirViagemActivity.EXTRA_PEDIDO_ID, pedidoId)
            startActivity(intent)
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /** Recarrega o pedido e as viagens toda vez que a tela volta ao primeiro plano. */
    override fun onResume() {
        super.onResume()
        // Também cobre a primeira exibição (onResume sempre roda logo após onCreate) e
        // recarrega ao voltar de "atribuir viagem", pro saldo e a lista ficarem atualizados.
        viewModel.carregar(pedidoId)
    }

    /** Reflete o [HistoricoPedidoUiState] atual: progresso do pedido, lista de viagens e toasts. */
    private fun exibirEstado(estado: HistoricoPedidoUiState) {
        if (estado.pedidoNaoEncontrado) {
            finish()
            return
        }

        textNumero.text = getString(R.string.historico_item_numero_formato, estado.pedidoId, estado.destinatarioNome)
        textProgresso.text = getString(
            R.string.historico_progresso_formato,
            formatarM3(estado.m3Alocados),
            formatarM3(estado.quantidadeTotalM3),
            formatarM3(estado.saldo)
        )
        botaoAtribuirViagem.visibility = if (estado.saldo > 0.0) View.VISIBLE else View.GONE

        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.historico_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAdapter(
                estado.viagens,
                aoCancelar = { viagemId, viagem -> confirmarCancelamento(viagemId, viagem) },
                aoVerRota = { viagemId ->
                    val intent = Intent(this, RotaViagemActivity::class.java)
                        .putExtra(RotaViagemActivity.EXTRA_VIAGEM_ID, viagemId)
                    startActivity(intent)
                }
            )

            val listaVazia = estado.viagens.isEmpty()
            textListaVazia.text = getString(R.string.historico_lista_vazia)
            textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
            recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
        }

        estado.resultadoCancelamento?.getConteudoSeNaoTratado()?.let { resultado ->
            when (resultado) {
                ResultadoCancelamento.SUCESSO ->
                    Toast.makeText(this, R.string.cancelar_viagem_sucesso, Toast.LENGTH_SHORT).show()
                ResultadoCancelamento.ESTADO_INVALIDO ->
                    Toast.makeText(this, R.string.cancelar_viagem_erro_estado, Toast.LENGTH_LONG).show()
                ResultadoCancelamento.ERRO_GENERICO ->
                    Toast.makeText(this, R.string.cancelar_viagem_erro_generico, Toast.LENGTH_LONG).show()
            }
        }
    }

    /** Pede o motivo do cancelamento num diálogo e só chama a ViewModel se o motivo não for vazio. */
    private fun confirmarCancelamento(viagemId: String, viagem: ViagemRemota) {
        val editMotivo = EditText(this).apply { hint = getString(R.string.cancelar_viagem_hint_motivo) }

        AlertDialog.Builder(this)
            .setTitle(R.string.cancelar_viagem_titulo)
            .setMessage(getString(R.string.viagem_motorista_formato, viagem.motoristaNome, viagem.caminhaoPlaca))
            .setView(editMotivo)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ ->
                val motivo = editMotivo.text.toString().trim()
                if (motivo.isEmpty()) {
                    Toast.makeText(this, R.string.cancelar_viagem_erro_motivo_vazio, Toast.LENGTH_SHORT).show()
                    return@setPositiveButton
                }
                viewModel.cancelar(viagemId, motivo, pedidoId)
            }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }
}
