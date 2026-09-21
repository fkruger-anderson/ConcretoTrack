package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ItemRateio
import java.util.Locale

/** Uma linha da tabela de rateio do roteiro: pedido, peso e km atribuído. */
class ItemRateioAdapter(
    private val itens: List<ItemRateio>
) : RecyclerView.Adapter<ItemRateioAdapter.ViewHolder>() {

    /** Referências das Views de um item da lista, encontradas uma única vez (evita findViewById repetido). */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPedido: TextView = view.findViewById(R.id.textPedido)
        val textPeso: TextView = view.findViewById(R.id.textPeso)
        val textRateio: TextView = view.findViewById(R.id.textRateio)
    }

    /** Infla o layout de um item da tabela de rateio. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rateio, parent, false)
        return ViewHolder(view)
    }

    /**
     * Preenche o item na posição [position]: pedido, peso teórico (m³ dedicados e distância) e o
     * percentual/km de fato rateado pra ele; destaca o fundo em amarelo quando esse é o item que
     * a tela pediu pra realçar (chegou de um clique numa viagem específica no histórico).
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context

        holder.textPedido.text = contexto.getString(
            R.string.detalhes_roteiro_item_formato, item.pedidoId, item.destinatarioNome
        )
        holder.textPeso.text = contexto.getString(
            R.string.detalhes_roteiro_item_peso_formato,
            formatarM3(item.volumeM3),
            formatarKm(item.distanciaDedicadaKm),
            formatarM3(item.pesoTeorico)
        )
        holder.textRateio.text = when {
            item.kmAtribuido != null -> {
                val percentual = String.format(Locale.getDefault(), "%.1f", item.percentualRateio)
                contexto.getString(
                    R.string.detalhes_roteiro_item_rateio_formato, percentual, formatarKm(item.kmAtribuido)
                )
            }
            item.cancelada -> contexto.getString(R.string.viagem_status_cancelada)
            else -> contexto.getString(R.string.detalhes_roteiro_item_sem_km)
        }

        holder.itemView.setBackgroundColor(
            if (item.destacado) Color.parseColor("#FFF3CD") else Color.TRANSPARENT
        )
    }

    /** Quantidade de itens da lista, exigido pelo RecyclerView pra saber quanto rolar. */
    override fun getItemCount(): Int = itens.size
}
