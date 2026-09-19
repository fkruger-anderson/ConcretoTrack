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

class ViagemAdapter(
    private val itens: List<Pair<String, ViagemRemota>>,
    private val aoCancelar: (viagemId: String, viagem: ViagemRemota) -> Unit
) : RecyclerView.Adapter<ViagemAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textMotorista: TextView = view.findViewById(R.id.textMotorista)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val botaoCancelar: Button = view.findViewById(R.id.botaoCancelar)
    }

    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem, parent, false)
        return ViewHolder(view)
    }

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
    }

    override fun getItemCount(): Int = itens.size
}
