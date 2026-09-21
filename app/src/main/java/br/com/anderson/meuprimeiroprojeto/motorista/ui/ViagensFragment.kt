package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.sessao.SessaoMotorista
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.ViagensAbertasViewModel

/** Aba "Viagens": lista das viagens do motorista que ainda não entraram em nenhum roteiro. */
class ViagensFragment : Fragment(R.layout.fragment_viagens) {

    private val viewModel: ViagensAbertasViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView

    /**
     * Liga as views do layout inflado, mostra a saudação com o nome em cache
     * ([SessaoMotorista]), configura o botão de sair (encerra a sessão do Firebase Auth e limpa o
     * cache local) e começa a observar o estado da ViewModel.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<TextView>(R.id.textBoasVindas).text =
            getString(R.string.viagens_boas_vindas, SessaoMotorista.nomeMotorista(requireContext()))

        view.findViewById<Button>(R.id.botaoSair).setOnClickListener {
            AutenticacaoRepositorio.sair()
            SessaoMotorista.sair(requireContext())
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }

        recyclerViagens = view.findViewById(R.id.recyclerViagens)
        recyclerViagens.layoutManager = LinearLayoutManager(requireContext())
        textListaVazia = view.findViewById(R.id.textListaVazia)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }
    }

    /** Recarrega a lista sempre que a aba volta a ficar visível (ex.: após voltar da tela de detalhes). */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    /**
     * Mostra a lista de viagens em aberto (ou a mensagem de lista vazia/erro) e liga o adapter
     * que abre [DetalhesViagemActivity] ao tocar num item.
     */
    private fun exibirEstado(estado: ViagensAbertasUiState) {
        if (estado.erroAoCarregar) {
            textListaVazia.text = getString(R.string.viagens_erro_carregar)
            textListaVazia.visibility = View.VISIBLE
            recyclerViagens.visibility = View.GONE
        } else {
            recyclerViagens.adapter = ViagemAbertaAdapter(estado.viagens) { item ->
                val intent = Intent(requireContext(), DetalhesViagemActivity::class.java)
                    .putExtra(DetalhesViagemActivity.EXTRA_VIAGEM_ID, item.viagemId)
                startActivity(intent)
            }

            val listaVazia = estado.viagens.isEmpty()
            textListaVazia.text = getString(R.string.viagens_lista_vazia)
            textListaVazia.visibility = if (listaVazia) View.VISIBLE else View.GONE
            recyclerViagens.visibility = if (listaVazia) View.GONE else View.VISIBLE
        }
    }

    /** Solta o adapter da lista; a view do fragment está sendo destruída. */
    override fun onDestroyView() {
        super.onDestroyView()
        recyclerViagens.adapter = null
    }
}
