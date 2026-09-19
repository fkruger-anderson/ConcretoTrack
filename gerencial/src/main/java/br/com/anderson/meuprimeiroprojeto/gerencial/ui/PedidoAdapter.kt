package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.PedidoComStatus
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3

class PedidoAdapter(
    private val itens: List<PedidoComStatus>,
    private val aoClicar: (PedidoComStatus) -> Unit
) : RecyclerView.Adapter<PedidoAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNumero: TextView = view.findViewById(R.id.textNumero)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textProgresso: TextView = view.findViewById(R.id.textProgresso)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pedido, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context

        holder.textNumero.text = contexto.getString(
            R.string.home_item_numero_formato, item.id, item.destinatarioNome
        )
        holder.textStatus.text = contexto.getString(
            when (item.status) {
                StatusPedido.PENDENTE -> R.string.status_pendente
                StatusPedido.EM_ANDAMENTO -> R.string.status_em_andamento
                StatusPedido.CONCLUIDO -> R.string.status_concluido
            }
        )
        holder.textProgresso.text = contexto.getString(
            R.string.home_progresso_formato,
            formatarM3(item.m3Alocados),
            formatarM3(item.quantidadeTotalM3),
            formatarM3(item.saldo)
        )
        holder.itemView.setOnClickListener { aoClicar(item) }
    }

    override fun getItemCount(): Int = itens.size
}
