package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarM3
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ItemRateio
import java.util.Locale

/**
 * O "Adapter" da tabela de rateio no detalhe do roteiro ([DetalhesRoteiroActivity]). Cada linha
 * mostra um pedido do roteiro, o peso dele (m³ × distância) e os km que couberam a ele.
 *
 * O Adapter é o intermediário entre a lista de dados e o RecyclerView (a lista rolável): diz
 * quantos itens há e como preencher cada linha.
 */
class ItemRateioAdapter(
    private val itens: List<ItemRateio>
) : RecyclerView.Adapter<ItemRateioAdapter.ViewHolder>() {

    /**
     * Guarda os componentes de uma linha, encontrados uma única vez com `findViewById`. Quando a
     * linha é reaproveitada na rolagem, eles já estão à mão.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textPedido: TextView = view.findViewById(R.id.textPedido)
        val textPeso: TextView = view.findViewById(R.id.textPeso)
        val textRateio: TextView = view.findViewById(R.id.textRateio)
    }

    /**
     * Chamado pelo RecyclerView quando precisa de uma linha nova: transforma ("infla") o layout
     * `item_rateio.xml` em componentes e embrulha num ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_rateio, parent, false)
        return ViewHolder(view)
    }

    /**
     * Chamado pelo RecyclerView para preencher a linha da posição [position]:
     * - pedido e destinatário;
     * - peso: volume, distância e o peso resultante;
     * - rateio: o percentual e os km, se já foram calculados; "cancelada", se a viagem foi
     *   cancelada; ou um aviso de "ainda sem km", se o roteiro não foi encerrado.
     *
     * A linha que o gerente tocou no histórico ganha fundo amarelo claro para se destacar. A cor
     * fica no `res/values/colors.xml` (regra dos slides: cores e textos ficam nos recursos, não no
     * código); `ContextCompat.getColor` busca essa cor pelo nome.
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
            if (item.destacado) ContextCompat.getColor(contexto, R.color.rateio_destaque) else Color.TRANSPARENT
        )
    }

    /** Quantos itens a tabela tem. O RecyclerView usa isso para saber até onde dá para rolar. */
    override fun getItemCount(): Int = itens.size
}
