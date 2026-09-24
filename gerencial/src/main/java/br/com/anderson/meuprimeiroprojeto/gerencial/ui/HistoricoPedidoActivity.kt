package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.app.AlertDialog
import android.app.DatePickerDialog
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
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.HistoricoPedidoUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.HistoricoPedidoViewModel
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ResultadoCancelamento
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * A tela de histórico de um pedido: o progresso (m³ reservados, total e saldo), o total de km, a
 * data de entrega e a lista de viagens — quem levou, quanto, quando e como terminou.
 *
 * Nesta tela o gerente pode: trocar a data de entrega, atribuir uma viagem nova (se ainda houver
 * saldo), cancelar uma viagem e abrir o detalhe do roteiro de uma viagem.
 */
class HistoricoPedidoActivity : AppCompatActivity() {

    companion object {
        /** Nome do "extra" do Intent que leva o número do pedido a mostrar. */
        const val EXTRA_PEDIDO_ID = "pedido_id"
    }

    private val viewModel: HistoricoPedidoViewModel by viewModels()
    // Formato de data, por exemplo "21/09/2026".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    private var pedidoId: Int = -1
    private lateinit var textNumero: TextView
    private lateinit var textProgresso: TextView
    private lateinit var textKmTotal: TextView
    private lateinit var botaoDataEntrega: Button
    private lateinit var botaoAtribuirViagem: Button
    private lateinit var textListaVazia: TextView
    private lateinit var recyclerViagens: RecyclerView

    /**
     * Chamado pelo Android quando a tela é criada. Lê o número do pedido que veio no Intent (sem
     * ele a tela fecha), liga os componentes e os botões, e começa a observar o ViewModel. Os dados
     * em si são carregados no [onResume].
     */
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
        textKmTotal = findViewById(R.id.textKmTotal)
        botaoDataEntrega = findViewById(R.id.botaoDataEntrega)
        botaoAtribuirViagem = findViewById(R.id.botaoAtribuirViagem)
        textListaVazia = findViewById(R.id.textListaVazia)
        recyclerViagens = findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(this)

        botaoDataEntrega.setOnClickListener { abrirSeletorDeDataEntrega() }
        botaoAtribuirViagem.setOnClickListener {
            val intent = Intent(this, AtribuirViagemActivity::class.java)
                .putExtra(AtribuirViagemActivity.EXTRA_PEDIDO_ID, pedidoId)
            startActivity(intent)
        }

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
    }

    /**
     * Chamado pelo Android toda vez que a tela volta a aparecer na frente (inclusive na primeira
     * vez). Recarrega o pedido e as viagens.
     */
    override fun onResume() {
        super.onResume()
        // Também cobre a primeira exibição (onResume sempre roda logo após onCreate) e
        // recarrega ao voltar de "atribuir viagem", pro saldo e a lista ficarem atualizados.
        viewModel.carregar(pedidoId)
    }

    /**
     * Desenha o estado atual: cabeçalho do pedido, botão da data (desligado se o pedido já foi
     * concluído), botão de atribuir (só aparece se ainda há saldo), a lista de viagens e os avisos
     * (Toasts) de resultado do cancelamento ou de erro ao trocar a data.
     */
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
        textKmTotal.text = getString(R.string.historico_km_total_formato, formatarKm(estado.totalKm))
        botaoDataEntrega.text = getString(
            R.string.home_data_entrega_formato, formatoData.format(Date(estado.dataEntrega))
        )
        // Pedido concluído não pode mais ter a data de entrega alterada.
        botaoDataEntrega.isEnabled = estado.status != StatusPedido.CONCLUIDO
        botaoAtribuirViagem.visibility = if (estado.saldo > 0.0) View.VISIBLE else View.GONE

        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.historico_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAdapter(
                estado.viagens,
                aoCancelar = { viagemId, viagem -> confirmarCancelamento(viagemId, viagem) },
                aoVerRota = { viagemId, roteiroId ->
                    val intent = Intent(this, DetalhesRoteiroActivity::class.java)
                        .putExtra(DetalhesRoteiroActivity.EXTRA_ROTEIRO_ID, roteiroId)
                        .putExtra(DetalhesRoteiroActivity.EXTRA_VIAGEM_DESTACADA_ID, viagemId)
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

        estado.erroAtualizarDataEntrega?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(this, R.string.historico_erro_atualizar_data_entrega, Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Abre o calendário do Android já na data de entrega atual do pedido. Quando o gerente escolhe
     * um dia, pede ao ViewModel para gravar a data nova (à meia-noite daquele dia).
     */
    private fun abrirSeletorDeDataEntrega() {
        val calendario = Calendar.getInstance().apply {
            timeInMillis = viewModel.estado.value?.dataEntrega ?: timeInMillis
        }

        DatePickerDialog(
            this,
            { _, ano, mes, dia ->
                val escolhida = Calendar.getInstance().apply {
                    set(ano, mes, dia, 0, 0, 0)
                    set(Calendar.MILLISECOND, 0)
                }
                viewModel.atualizarDataEntrega(pedidoId, escolhida.timeInMillis)
            },
            calendario.get(Calendar.YEAR),
            calendario.get(Calendar.MONTH),
            calendario.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    /**
     * Abre uma caixa de confirmação (`AlertDialog`) para cancelar a viagem, com um campo para o
     * motivo. O motivo é obrigatório: se o gerente confirmar com o campo vazio, aparece um aviso e
     * nada é cancelado. Com motivo, pede ao ViewModel para cancelar.
     */
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
