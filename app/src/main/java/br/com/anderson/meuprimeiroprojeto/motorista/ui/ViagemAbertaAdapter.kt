package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.dados.ViagemAbertaComPedido
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusViagem
import br.com.anderson.meuprimeiroprojeto.motorista.formatarM3

/**
 * O "Adapter" da lista da aba Viagens ([ViagensFragment]).
 *
 * Um RecyclerView (a lista) não sabe nada sobre viagens: ele só sabe rolar e reaproveitar
 * linhas. O Adapter é o intermediário que diz a ele quantos itens existem e como preencher cada
 * linha com os dados de uma viagem.
 *
 * "Reaproveitar" é o truque do RecyclerView: em vez de criar uma linha para cada item da lista
 * (o que gastaria muita memória), ele cria só as que cabem na tela e, ao rolar, reusa as que
 * saíram por cima para mostrar os itens que entram por baixo.
 *
 * Este Adapter não guarda nada além do que recebe: a lista pronta e a função [aoClicar], que diz
 * o que fazer quando um item é tocado. Só nome, endereço e m³ aparecem — nunca o CPF/CNPJ do
 * destinatário (LGPD).
 */
class ViagemAbertaAdapter(
    private val itens: List<ViagemAbertaComPedido>,
    private val aoClicar: (ViagemAbertaComPedido) -> Unit
) : RecyclerView.Adapter<ViagemAbertaAdapter.ViewHolder>() {

    /**
     * O "ViewHolder" guarda os componentes de uma linha da lista, encontrados uma única vez com
     * `findViewById` quando a linha é criada. Quando a linha é reaproveitada, os componentes já
     * estão à mão, e a rolagem fica suave.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textEndereco: TextView = view.findViewById(R.id.textEndereco)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
    }

    /**
     * Chamado pelo RecyclerView quando ele precisa de uma linha nova. "Infla" (transforma o XML em
     * componentes de verdade) o layout `item_viagem_aberta.xml` e embrulha num ViewHolder.
     */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_aberta, parent, false)
        return ViewHolder(view)
    }

    /**
     * Chamado pelo RecyclerView para colocar os dados do item da posição [position] numa linha
     * (nova ou reaproveitada): destinatário, endereço, m³ + caminhão e a situação.
     */
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = itens[position]
        val contexto = holder.itemView.context
        holder.textDestinatario.text = item.pedido?.destinatarioNome ?: ""
        holder.textEndereco.text = item.pedido?.enderecoResumido() ?: ""
        holder.textDetalhes.text = contexto.getString(
            R.string.viagens_item_detalhes_formato, formatarM3(item.viagem.metrosCubicos), item.viagem.caminhaoPlaca
        )
        holder.textStatus.text = contexto.getString(
            if (item.viagem.status == StatusViagem.EM_ROTA.name) {
                R.string.viagens_status_em_rota
            } else {
                R.string.viagens_status_atribuida
            }
        )
        holder.itemView.setOnClickListener { aoClicar(item) }
    }

    /** Quantos itens a lista tem. O RecyclerView usa isso para saber até onde dá para rolar. */
    override fun getItemCount(): Int = itens.size
}
