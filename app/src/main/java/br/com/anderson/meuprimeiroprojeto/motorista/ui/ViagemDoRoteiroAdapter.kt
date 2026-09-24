package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagemDoRoteiro
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * O "Adapter" da lista de viagens dentro da aba Roteiro ([RoteiroFragment]).
 *
 * O Adapter é o intermediário entre a lista de dados e o RecyclerView (o componente que mostra a
 * lista rolável): diz quantos itens há e como preencher cada linha. Cada linha mostra o
 * destinatário, a situação (pendente, entregue às HH:mm, cancelada às HH:mm) e, enquanto a
 * viagem está pendente, um botão "Entregue".
 *
 * [aoMarcarEntregue] é a função que o fragment passa para ser chamada quando o botão de uma linha
 * é tocado. O Adapter não decide nada: só avisa qual viagem foi tocada.
 */
class ViagemDoRoteiroAdapter(
    private val itens: List<ViagemDoRoteiro>,
    private val aoMarcarEntregue: (viagemId: String) -> Unit
) : RecyclerView.Adapter<ViagemDoRoteiroAdapter.ViewHolder>() {

    // Formato de hora e minuto, por exemplo "14:35".
    private val formatoHorario = SimpleDateFormat("HH:mm", Locale.forLanguageTag("pt-BR"))

    /**
     * Guarda os componentes de uma linha, encontrados uma única vez com `findViewById`. Quando a
     * linha é reaproveitada na rolagem, eles já estão à mão.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val botaoEntregue: Button = view.findViewById(R.id.botaoEntregue)
    }

    /**
     * Chamado pelo RecyclerView quando precisa de uma linha nova: transforma o layout
     * `item_viagem_do_roteiro.xml` em componentes ("inflar") e embrulha num ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_do_roteiro, parent, false)
        return ViewHolder(view)
    }

    /**
     * Chamado pelo RecyclerView para preencher uma linha com o item da posição [position].
     *
     * A viagem é "pendente" se ainda não foi concluída nem cancelada; só nesse caso o botão
     * "Entregue" aparece. O texto da situação mostra o horário da entrega ou do cancelamento.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context
        holder.textDestinatario.text = item.destinatarioNome

        val pendente = item.viagem.status != StatusViagem.CONCLUIDA.name &&
            item.viagem.status != StatusViagem.CANCELADA.name
        holder.textStatus.text = when (item.viagem.status) {
            StatusViagem.CONCLUIDA.name -> contexto.getString(
                R.string.roteiro_item_entregue_formato, formatarHorario(item.viagem.dataFim)
            )
            StatusViagem.CANCELADA.name -> contexto.getString(
                R.string.roteiro_item_cancelada_formato, formatarHorario(item.viagem.dataCancelamento)
            )
            else -> contexto.getString(R.string.roteiro_item_pendente)
        }
        holder.botaoEntregue.visibility = if (pendente) View.VISIBLE else View.GONE
        holder.botaoEntregue.setOnClickListener { aoMarcarEntregue(item.viagemId) }
    }

    /** Quantos itens a lista tem. O RecyclerView usa isso para saber até onde dá para rolar. */
    override fun getItemCount(): Int = itens.size

    /**
     * Transforma um instante (milissegundos) em "HH:mm". Se o horário não vier preenchido, devolve
     * texto vazio em vez de quebrar.
     */
    private fun formatarHorario(horario: Long?): String =
        if (horario != null) formatoHorario.format(Date(horario)) else ""
}
