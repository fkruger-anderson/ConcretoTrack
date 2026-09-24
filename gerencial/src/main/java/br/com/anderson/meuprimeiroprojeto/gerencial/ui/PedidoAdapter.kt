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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * O "Adapter" da lista da aba Pedidos.
 *
 * O RecyclerView (a lista rolável) não sabe nada sobre pedidos: ele só rola e reaproveita linhas.
 * O Adapter é o intermediário que diz quantos itens existem e como preencher cada linha.
 * "Reaproveitar" quer dizer que o RecyclerView cria só as linhas que cabem na tela e, ao rolar,
 * reusa as que saíram para mostrar os itens que entram — economizando memória.
 *
 * Este Adapter recebe a lista já filtrada do ViewModel e a função [aoClicar]. Ele só desenha e
 * avisa o toque; não busca dados em lugar nenhum.
 */
class PedidoAdapter(
    private val itens: List<PedidoComStatus>,
    private val aoClicar: (PedidoComStatus) -> Unit
) : RecyclerView.Adapter<PedidoAdapter.ViewHolder>() {

    /**
     * O "ViewHolder" guarda os componentes de uma linha, encontrados uma única vez com
     * `findViewById`. Quando a linha é reaproveitada, eles já estão à mão, e a rolagem fica suave.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textNumero: TextView = view.findViewById(R.id.textNumero)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
        val textProgresso: TextView = view.findViewById(R.id.textProgresso)
        val textDataEntrega: TextView = view.findViewById(R.id.textDataEntrega)
    }

    // Formato de data, por exemplo "21/09/2026".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("pt-BR"))

    /**
     * Chamado pelo RecyclerView quando precisa de uma linha nova: transforma ("infla") o layout
     * `item_pedido.xml` em componentes e embrulha num ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_pedido, parent, false)
        return ViewHolder(view)
    }

    /**
     * Chamado pelo RecyclerView para preencher uma linha com o pedido da posição [position]: número
     * e destinatário, situação, progresso em m³ (reservado / total / saldo) e data de entrega.
     */
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
        holder.textDataEntrega.text = contexto.getString(
            R.string.home_data_entrega_formato, formatoData.format(Date(item.dataEntrega))
        )
        holder.itemView.setOnClickListener { aoClicar(item) }
    }

    /** Quantos itens a lista tem. O RecyclerView usa isso para saber até onde dá para rolar. */
    override fun getItemCount(): Int = itens.size
}
