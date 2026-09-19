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

/** Aba "Pedidos": lista com filtro por status + atalho para cadastrar um novo. */
class PedidosFragment : Fragment(R.layout.fragment_pedidos) {

    private val viewModel: PedidosViewModel by viewModels()

    private lateinit var recyclerPedidos: RecyclerView
    private lateinit var textListaVazia: TextView
    private lateinit var spinnerFiltro: Spinner

    // null = "Todos". A posição no spinner (índice desta lista) decide o filtro aplicado.
    private val opcoesFiltro = listOf(null, StatusPedido.PENDENTE, StatusPedido.EM_ANDAMENTO, StatusPedido.CONCLUIDO)

    /** Liga a RecyclerView, o spinner de filtro e o botão de novo pedido; observa a ViewModel. */
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
            /** Repassa a posição escolhida no spinner como o novo filtro de status. */
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

    /** Recarrega a lista de pedidos toda vez que a tela volta ao primeiro plano. */
    override fun onResume() {
        super.onResume()
        // Recarrega ao voltar do cadastro de pedido, pra mostrar o pedido novo na lista.
        viewModel.carregar()
    }

    /** Reflete o [PedidosUiState] atual: monta o adapter com a lista filtrada e trata lista vazia. */
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
