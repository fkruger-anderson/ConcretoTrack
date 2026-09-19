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
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Adapter da lista de viagens de um pedido (histórico). Recebe a lista já pronta da ViewModel e
 * dois callbacks (cancelar e ver rota) — a decisão de habilitar cada ação é feita aqui, olhando
 * o status/dataInicio de cada viagem, mas quem executa a ação é sempre a ViewModel/Activity.
 */
class ViagemAdapter(
    private val itens: List<Pair<String, ViagemRemota>>,
    private val aoCancelar: (viagemId: String, viagem: ViagemRemota) -> Unit,
    private val aoVerRota: (viagemId: String) -> Unit
) : RecyclerView.Adapter<ViagemAdapter.ViewHolder>() {

    /** Referências das Views de um item da lista, encontradas uma única vez (evita findViewById repetido). */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMotorista: TextView = view.findViewById(R.id.textMotorista)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val botaoCancelar: Button = view.findViewById(R.id.botaoCancelar)
    }

    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    /** Infla o layout de um item da lista de viagens. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem, parent, false)
        return ViewHolder(view)
    }

    /** Preenche o item na posição [position]: motorista/caminhão, status e detalhes (m³, datas, motivo). */
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

        val podeCancelar = item.status == StatusViagem.ATRIBUIDA.name || item.status == StatusViagem.EM_ROTA.name
        holder.botaoCancelar.visibility = if (podeCancelar) View.VISIBLE else View.GONE
        holder.botaoCancelar.setOnClickListener { aoCancelar(viagemId, item) }

        // Só tem rota pra mostrar se o motorista chegou a iniciar o trânsito.
        val temRota = item.dataInicio != null
        holder.itemView.isClickable = temRota
        holder.itemView.setOnClickListener { if (temRota) aoVerRota(viagemId) }
    }

    /** Quantidade de itens da lista, exigido pelo RecyclerView pra saber quanto rolar. */
    override fun getItemCount(): Int = itens.size
}
