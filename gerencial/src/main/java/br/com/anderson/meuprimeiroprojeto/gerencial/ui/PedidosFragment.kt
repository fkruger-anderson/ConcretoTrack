package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.PedidosUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.PedidosViewModel

/**
 * A aba "Pedidos": a lista de pedidos, com um filtro por situação e um botão para cadastrar um
 * pedido novo. Tocar num pedido abre o histórico dele.
 *
 * Um `Fragment` é um pedaço de tela que vive dentro de uma Activity (aqui, a [MainActivity]). O
 * `Fragment(R.layout.fragment_pedidos)` no cabeçalho já diz qual layout usar: o Android monta a
 * tela sozinho e o código só liga os componentes em [onViewCreated].
 */
class PedidosFragment : Fragment(R.layout.fragment_pedidos) {

    private val viewModel: PedidosViewModel by viewModels()

    private lateinit var recyclerPedidos: RecyclerView
    private lateinit var textListaVazia: TextView
    private lateinit var spinnerFiltro: Spinner

    // null = "Todos". A posição no spinner (índice desta lista) decide o filtro aplicado.
    private val opcoesFiltro = listOf(null, StatusPedido.PENDENTE, StatusPedido.EM_ANDAMENTO, StatusPedido.CONCLUIDO)

    /**
     * Chamado pelo Android logo depois que a tela do fragment foi montada. Liga os componentes:
     * - a lista (RecyclerView), com itens um embaixo do outro (`LinearLayoutManager`);
     * - a caixa de seleção do filtro (Spinner). Um `ArrayAdapter` simples transforma a lista de
     *   textos em opções; quando o gerente escolhe uma, o ViewModel refaz o filtro;
     * - o botão "Novo pedido", que abre a [NovoPedidoActivity];
     * - a observação do estado do ViewModel (`viewLifecycleOwner` faz ela parar sozinha quando a
     *   tela do fragment é destruída).
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        recyclerPedidos = view.findViewById(R.id.recyclerPedidos)
        recyclerPedidos.layoutManager = LinearLayoutManager(requireContext())
        textListaVazia = view.findViewById(R.id.textListaVazia)
        spinnerFiltro = view.findViewById(R.id.spinnerFiltro)

        val rotulos = listOf(
            getString(R.string.filtro_todos),
            getString(R.string.status_pendente),
            getString(R.string.status_em_andamento),
            getString(R.string.status_concluido)
        )
        spinnerFiltro.adapter = ArrayAdapter(requireContext(), android.R.layout.simple_spinner_dropdown_item, rotulos)
        spinnerFiltro.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            /** Chamado quando o gerente escolhe uma opção: passa a posição escolhida como novo filtro. */
            override fun onItemSelected(parent: AdapterView<*>?, v: View?, position: Int, id: Long) {
                viewModel.filtrarPor(opcoesFiltro.getOrNull(position))
            }

            // Exigido pela interface OnItemSelectedListener, mas não é usado aqui.
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        view.findViewById<Button>(R.id.botaoNovoPedido).setOnClickListener {
            startActivity(Intent(requireContext(), NovoPedidoActivity::class.java))
        }

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }
    }

    /**
     * Chamado pelo Android toda vez que a aba volta a aparecer na frente (inclusive na primeira
     * vez). Recarrega a lista — assim, ao voltar do cadastro, o pedido novo já aparece.
     */
    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar do cadastro de pedido, pra mostrar o pedido novo na lista.
        viewModel.carregar()
    }

    /**
     * Desenha o estado atual: entrega a lista filtrada ao Adapter (tocar num item abre o
     * [HistoricoPedidoActivity], levando o número do pedido como "extra" do Intent) e, se a lista
     * estiver vazia, mostra o aviso de lista vazia ou de erro.
     */
    private fun exibirEstado(estado: PedidosUiState) {
        recyclerPedidos.adapter = PedidoAdapter(estado.pedidosFiltrados) { item ->
            val intent = Intent(requireContext(), HistoricoPedidoActivity::class.java)
                .putExtra(HistoricoPedidoActivity.EXTRA_PEDIDO_ID, item.id)
            startActivity(intent)
        }

        val listaVazia = estado.pedidosFiltrados.isEmpty()
        textListaVazia.text = getString(
            if (estado.erroAoCarregar) R.string.home_erro_carregar else R.string.home_lista_vazia
        )
        textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
        recyclerPedidos.visibility = if (listaVazia) View.GONE else View.VISIBLE
    }
}
