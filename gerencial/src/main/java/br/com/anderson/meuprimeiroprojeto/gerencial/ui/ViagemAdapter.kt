package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
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
        val textM3: TextView = view.findViewById(R.id.textM3)
        val textPeriodo: TextView = view.findViewById(R.id.textPeriodo)
        val textMotivo: TextView = view.findViewById(R.id.textMotivo)
        val textKm: TextView = view.findViewById(R.id.textKm)
        val botaoCancelar: Button = view.findViewById(R.id.botaoCancelar)
        val linhaSuperior: View = view.findViewById(R.id.linhaSuperior)
        val linhaInferior: View = view.findViewById(R.id.linhaInferior)
        val pontoTimeline: View = view.findViewById(R.id.pontoTimeline)
    }

    // Formato de data curto, por exemplo "21/09/26".
    private val formatoData = SimpleDateFormat("dd/MM/yy", Locale.forLanguageTag("pt-BR"))

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
     * A situação não aparece em texto: é a cor do ponto da timeline (e a descrição dele, lida pelos
     * leitores de tela). Ao lado do ícone de peso vão os m³; ao lado do calendário, o período:
     * - já saiu e terminou: "início - fim" (ex.: "18/09/26 - 18/09/26");
     * - já saiu e ainda não terminou: só o início;
     * - ainda não saiu: a data da atribuição.
     * Viagem cancelada mostra também o motivo.
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
        // A situação vira só a descrição do ponto, para os leitores de tela.
        holder.pontoTimeline.contentDescription = contexto.getString(
            when (item.status) {
                StatusViagem.ATRIBUIDA.name -> R.string.viagem_status_atribuida
                StatusViagem.EM_ROTA.name -> R.string.viagem_status_em_rota
                StatusViagem.CANCELADA.name -> R.string.viagem_status_cancelada
                else -> R.string.viagem_status_concluida
            }
        )

        // Timeline: a linha não passa acima do primeiro item nem abaixo do último, e o ponto ganha
        // a cor da situação (cinza atribuída, amarelo em rota, verde concluída, vermelho cancelada).
        holder.linhaSuperior.visibility = if (position == 0) View.INVISIBLE else View.VISIBLE
        holder.linhaInferior.visibility = if (position == itens.lastIndex) View.INVISIBLE else View.VISIBLE
        val corPonto = when (item.status) {
            StatusViagem.ATRIBUIDA.name -> R.color.timeline_atribuida
            StatusViagem.EM_ROTA.name -> R.color.timeline_em_rota
            StatusViagem.CANCELADA.name -> R.color.timeline_cancelada
            else -> R.color.timeline_concluida
        }
        holder.pontoTimeline.backgroundTintList =
            ColorStateList.valueOf(ContextCompat.getColor(contexto, corPonto))

        holder.textM3.text = contexto.getString(R.string.viagem_m3_formato, formatarM3(item.metrosCubicos))

        val dataInicio = item.dataInicio
        val dataFim = item.dataFim
        holder.textPeriodo.text = when {
            dataInicio == null -> formatoData.format(Date(item.dataAtribuicao))
            dataFim == null -> formatoData.format(Date(dataInicio))
            else -> contexto.getString(
                R.string.viagem_periodo_formato,
                formatoData.format(Date(dataInicio)),
                formatoData.format(Date(dataFim))
            )
        }

        val motivo = item.motivoCancelamento
        if (item.status == StatusViagem.CANCELADA.name && !motivo.isNullOrBlank()) {
            holder.textMotivo.text = contexto.getString(R.string.viagem_motivo_formato, motivo)
            holder.textMotivo.visibility = View.VISIBLE
        } else {
            holder.textMotivo.visibility = View.GONE
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
