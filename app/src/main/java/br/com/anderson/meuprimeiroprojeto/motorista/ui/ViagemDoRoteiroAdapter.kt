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

/** Lista das viagens do roteiro atual, com um botão "marcar como entregue" por item pendente. */
class ViagemDoRoteiroAdapter(
    private val itens: List<ViagemDoRoteiro>,
    private val aoMarcarEntregue: (viagemId: String) -> Unit
) : RecyclerView.Adapter<ViagemDoRoteiroAdapter.ViewHolder>() {

    /**
     * Guarda as referências das views de um item já inflado (`findViewById` só uma vez por item
     * reciclado, não a cada scroll) — é o papel de um ViewHolder no padrão do RecyclerView.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val botaoEntregue: Button = view.findViewById(R.id.botaoEntregue)
    }

    /** Infla o layout de um item da lista (`item_viagem_do_roteiro.xml`) e cria o ViewHolder dele. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_do_roteiro, parent, false)
        return ViewHolder(view)
    }

    /**
     * Preenche as views de um ViewHolder com os dados do item da posição e mostra o botão de
     * entregue só enquanto a viagem ainda não foi concluída nem cancelada.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context
        holder.textDestinatario.text = item.destinatarioNome

        val pendente = item.viagem.status != StatusViagem.CONCLUIDA.name &&
            item.viagem.status != StatusViagem.CANCELADA.name
        holder.textStatus.text = contexto.getString(
            when (item.viagem.status) {
                StatusViagem.CONCLUIDA.name -> R.string.roteiro_item_entregue
                StatusViagem.CANCELADA.name -> R.string.roteiro_item_cancelada
                else -> R.string.roteiro_item_pendente
            }
        )
        holder.botaoEntregue.visibility = if (pendente) View.VISIBLE else View.GONE
        holder.botaoEntregue.setOnClickListener { aoMarcarEntregue(item.viagemId) }
    }

    /** Total de itens da lista; o RecyclerView usa isso pra saber quantas posições existem. */
    override fun getItemCount(): Int = itens.size
}
