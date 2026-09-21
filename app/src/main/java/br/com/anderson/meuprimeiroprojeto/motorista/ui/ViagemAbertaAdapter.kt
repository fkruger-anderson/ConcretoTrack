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
 * Adapter do RecyclerView com as viagens em aberto do motorista (atribuídas ou em rota) na
 * [ViagensAbertasActivity][br.com.anderson.meuprimeiroprojeto.motorista.ui.ViagensAbertasActivity].
 * Não guarda estado próprio: recebe a lista pronta e um callback de clique, e só desenha.
 * CPF/CNPJ do destinatário não aparece aqui — só nome, endereço e m³ (regra de LGPD do projeto).
 */
class ViagemAbertaAdapter(
    private val itens: List<ViagemAbertaComPedido>,
    private val aoClicar: (ViagemAbertaComPedido) -> Unit
) : RecyclerView.Adapter<ViagemAbertaAdapter.ViewHolder>() {

    /**
     * Guarda as referências das views de um item já inflado (`findViewById` só uma vez por item
     * reciclado, não a cada scroll) — é o papel de um ViewHolder no padrão do RecyclerView.
     */
    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val textDestinatario: TextView = view.findViewById(R.id.textDestinatario)
        val textEndereco: TextView = view.findViewById(R.id.textEndereco)
        val textDetalhes: TextView = view.findViewById(R.id.textDetalhes)
        val textStatus: TextView = view.findViewById(R.id.textStatus)
    }

    /** Infla o layout de um item da lista (`item_viagem_aberta.xml`) e cria o ViewHolder dele. */
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_viagem_aberta, parent, false)
        return ViewHolder(view)
    }

    /** Preenche as views de um ViewHolder (reciclado ou novo) com os dados do item da posição. */
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

    /** Total de itens da lista; o RecyclerView usa isso pra saber quantas posições existem. */
    override fun getItemCount(): Int = itens.size
}
