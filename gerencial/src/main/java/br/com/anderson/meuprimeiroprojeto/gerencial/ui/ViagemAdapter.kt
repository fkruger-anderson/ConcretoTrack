package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.ViagemRemota
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusViagem
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * O "Adapter" da lista de viagens no histórico de um pedido ([HistoricoPedidoActivity]).
 *
 * O Adapter é o intermediário entre a lista de dados e o RecyclerView (a lista rolável): diz quantos
 * itens há e como preencher cada linha. Cada linha mostra motorista e placa, situação, detalhes
 * (m³ e datas, ou o motivo do cancelamento), os km (se já calculados) e um botão Cancelar.
 *
 * Recebe duas funções da tela: [aoCancelar] (botão Cancelar) e [aoVerRota] (toque na linha, abre o
 * detalhe do roteiro). O Adapter decide QUANDO cada ação está disponível, olhando a situação da
 * viagem, mas quem EXECUTA a ação é sempre a tela/ViewModel.
 */
class ViagemAdapter(
    private val itens: List<Pair<String, ViagemRemota>>,
    private val aoCancelar: (viagemId: String, viagem: ViagemRemota) -> Unit,
    private val aoVerRota: (viagemId: String, roteiroId: String) -> Unit
) : RecyclerView.Adapter<ViagemAdapter.ViewHolder>() {

    /**
     * Guarda os componentes de uma linha, encontrados uma única vez com `findViewById`. Quando a
     * linha é reaproveitada na rolagem, eles já estão à mão.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMotorista: TextView = view.findViewById(R.id.textMotorista)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val textKm: TextView = view.findViewById(R.id.textKm)
        val botaoCancelar: Button = view.findViewById(R.id.botaoCancelar)
    }

    // Formato de data e hora, por exemplo "21/09/2026 14:35".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    /**
     * Chamado pelo RecyclerView quando precisa de uma linha nova: transforma ("infla") o layout
     * `item_viagem.xml` em componentes e embrulha num ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem, parent, false)
        return ViewHolder(view)
    }

    /**
     * Chamado pelo RecyclerView para preencher uma linha com a viagem da posição [position].
     *
     * O texto de detalhes muda conforme a situação:
     * - cancelada: m³ e o motivo;
     * - ainda não saiu (sem data de início): m³ e a data da atribuição;
     * - já saiu: m³, início e, se já entregue, o fim.
     *
     * O botão Cancelar só aparece para viagens ATRIBUIDA ou EM_ROTA. Tocar na linha abre o roteiro,
     * mas só se a viagem já estiver em algum roteiro.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val (viagemId, item) = itens[position]
        val contexto = holder.itemView.context

        holder.textMotorista.text = contexto.getString(
            R.string.viagem_motorista_formato, item.motoristaNome, item.caminhaoPlaca
        )
        holder.textStatus.text = contexto.getString(
            when (item.status) {
                StatusViagem.ATRIBUIDA.name -> R.string.viagem_status_atribuida
                StatusViagem.EM_ROTA.name -> R.string.viagem_status_em_rota
                StatusViagem.CANCELADA.name -> R.string.viagem_status_cancelada
                else -> R.string.viagem_status_concluida
            }
        )

        val dataInicio = item.dataInicio
        holder.textDetalhes.text = when {
            item.status == StatusViagem.CANCELADA.name -> contexto.getString(
                R.string.viagem_detalhes_cancelada_formato,
                formatarM3(item.metrosCubicos),
                item.motivoCancelamento ?: ""
            )
            dataInicio == null -> contexto.getString(
                R.string.viagem_detalhes_atribuida_formato,
                formatarM3(item.metrosCubicos),
                formatoData.format(Date(item.dataAtribuicao))
            )
            else -> {
                val fim = item.dataFim?.let {
                    contexto.getString(R.string.viagem_fim_formato, formatoData.format(Date(it)))
                } ?: ""
                contexto.getString(
                    R.string.viagem_detalhes_formato,
                    formatarM3(item.metrosCubicos),
                    formatoData.format(Date(dataInicio)),
                    fim
                )
            }
        }

        val km = item.kmPercorridos
        if (km != null) {
            holder.textKm.text = contexto.getString(R.string.viagem_km_formato, formatarKm(km))
            holder.textKm.visibility = View.VISIBLE
        } else {
            holder.textKm.visibility = View.GONE
        }

        val podeCancelar = item.status == StatusViagem.ATRIBUIDA.name || item.status == StatusViagem.EM_ROTA.name
        holder.botaoCancelar.visibility = if (podeCancelar) View.VISIBLE else View.GONE
        holder.botaoCancelar.setOnClickListener { aoCancelar(viagemId, item) }

        val roteiroId = item.roteiroId
        holder.itemView.isClickable = roteiroId != null
        holder.itemView.setOnClickListener {
            if (roteiroId != null) aoVerRota(viagemId, roteiroId)
        }
    }

    /** Quantos itens a lista tem. O RecyclerView usa isso para saber até onde dá para rolar. */
    override fun getItemCount(): Int = itens.size
}
