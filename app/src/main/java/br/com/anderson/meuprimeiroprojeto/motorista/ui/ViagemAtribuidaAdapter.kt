package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAtribuidaComPedido
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3

class ViagemAtribuidaAdapter(
    private val itens: List<ViagemAtribuidaComPedido>,
    private val aoClicar: (ViagemAtribuidaComPedido) -> Unit
) : RecyclerView.Adapter<ViagemAtribuidaAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textEndereco: TextView = view.findViewById(R.id.textEndereco)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_atribuida, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        holder.textDestinatario.text = item.pedido?.destinatarioNome ?: ""
        holder.textEndereco.text = item.pedido?.enderecoResumido() ?: ""
        holder.textDetalhes.text = holder.itemView.context.getString(
            R.string.viagens_item_detalhes_formato, formatarM3(item.viagem.metrosCubicos), item.viagem.caminhaoPlaca
        )
        holder.itemView.setOnClickListener { aoClicar(item) }
    }

    override fun getItemCount(): Int = itens.size
}
