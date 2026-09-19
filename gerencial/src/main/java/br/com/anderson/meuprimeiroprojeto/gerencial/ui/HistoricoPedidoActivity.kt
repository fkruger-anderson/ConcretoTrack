package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.PedidoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout

/** Histórico de viagens de um pedido: quem levou, quanto, quando, e como terminou. */
class HistoricoPedidoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_PEDIDO_ID = "pedido_id"
        private const val TEMPO_LIMITE_MS = 10_000L
    }

    private var pedidoId: Int = -1
    private lateinit var textNumero: TextView
    private lateinit var textProgresso: TextView
    private lateinit var textListaVazia: TextView
    private lateinit var recyclerViagens: RecyclerView

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
        textListaVazia = findViewById(R.id.textListaVazia)
        recyclerViagens = findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(this)

        carregar()
    }

    private fun carregar() {
        lifecycleScope.launch {
            try {
                val pedido = PedidoRepositorio.buscarPorId(pedidoId)
                if (pedido == null) {
                    finish()
                    return@launch
                }
                textNumero.text = getString(
                    R.string.historico_item_numero_formato, pedidoId, pedido.destinatarioNome
                )
                textProgresso.text = getString(
                    R.string.historico_progresso_formato,
                    formatarM3(pedido.m3Alocados),
                    formatarM3(pedido.quantidadeTotalM3),
                    formatarM3(pedido.quantidadeTotalM3 - pedido.m3Alocados)
                )

                val viagens = ViagemRepositorio.listarPorPedido(pedidoId)
                recyclerViagens.adapter = ViagemAdapter(viagens) { viagemId, viagem ->
                    confirmarCancelamento(viagemId, viagem)
                }

                val listaVazia = viagens.isEmpty()
                textListaVazia.text = getString(R.string.historico_lista_vazia)
                textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
                recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
            } catch (erro: Exception) {
                textListaVazia.text = getString(R.string.historico_erro_carregar)
                textListaVazia.visibility = View.VISIBLE
                recyclerViagens.visibility = View.GONE
            }
        }
    }

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
                cancelar(viagemId, motivo)
            }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun cancelar(viagemId: String, motivo: String) {
        lifecycleScope.launch {
            try {
                withTimeout(TEMPO_LIMITE_MS) {
                    ViagemRepositorio.cancelar(viagemId, motivo)
                }
                Toast.makeText(this@HistoricoPedidoActivity, R.string.cancelar_viagem_sucesso, Toast.LENGTH_SHORT)
                    .show()
                carregar()
            } catch (erro: ViagemRepositorio.EstadoInvalidoException) {
                Toast.makeText(this@HistoricoPedidoActivity, R.string.cancelar_viagem_erro_estado, Toast.LENGTH_LONG)
                    .show()
                carregar()
            } catch (erro: TimeoutCancellationException) {
                Toast.makeText(this@HistoricoPedidoActivity, R.string.cancelar_viagem_erro_generico, Toast.LENGTH_LONG)
                    .show()
            } catch (erro: Exception) {
                Toast.makeText(this@HistoricoPedidoActivity, R.string.cancelar_viagem_erro_generico, Toast.LENGTH_LONG)
                    .show()
            }
        }
    }
}
