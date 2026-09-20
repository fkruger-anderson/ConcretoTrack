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

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPedido: TextView = view.findViewById(R.id.textPedido)
        val textPeso: TextView = view.findViewById(R.id.textPeso)
        val textRateio: TextView = view.findViewById(R.id.textRateio)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rateio, parent, false)
        return ViewHolder(view)
    }

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
        holder.textRateio.text = if (item.kmAtribuido != null) {
            val percentual = String.format(Locale.getDefault(), "%.1f", item.percentualRateio)
            contexto.getString(
                R.string.detalhes_roteiro_item_rateio_formato, percentual, formatarKm(item.kmAtribuido)
            )
        } else {
            contexto.getString(R.string.detalhes_roteiro_item_sem_km)
        }

        holder.itemView.setBackgroundColor(
            if (item.destacado) Color.parseColor("#FFF3CD") else Color.TRANSPARENT
        )
    }

    override fun getItemCount(): Int = itens.size
}
