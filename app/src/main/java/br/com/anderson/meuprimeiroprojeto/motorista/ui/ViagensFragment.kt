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

/**
 * A aba "Viagens": a lista das viagens do motorista que ainda não foram colocadas em nenhum
 * roteiro. Tocar numa viagem abre a tela de detalhes, onde ela pode ser adicionada ao roteiro.
 *
 * Um `Fragment` é um pedaço de tela que vive dentro de uma Activity (aqui, dentro da
 * [MotoristaHomeActivity]). O `Fragment(R.layout.fragment_viagens)` no cabeçalho da classe já diz
 * qual layout XML usar: o Android monta ("infla") a tela sozinho, e o código só precisa ligar os
 * componentes em [onViewCreated].
 */
class ViagensFragment : Fragment(R.layout.fragment_viagens) {

    // O ViewModel desta aba; sobrevive quando o celular gira.
    private val viewModel: ViagensAbertasViewModel by viewModels()

    private lateinit var recyclerViagens: RecyclerView
    private lateinit var textListaVazia: TextView

    /**
     * Chamado pelo Android logo depois que a tela do fragment foi montada. É o lugar para ligar os
     * componentes:
     * - mostra "Olá, <nome>" usando o nome guardado no celular ([SessaoMotorista]);
     * - configura o botão Sair: faz logout no Firebase, apaga o nome guardado, abre o login e fecha
     *   a tela atual;
     * - prepara a lista (RecyclerView). O `LinearLayoutManager` diz que os itens ficam um embaixo
     *   do outro;
     * - começa a observar o estado do ViewModel. `viewLifecycleOwner` faz a observação parar
     *   sozinha quando a tela do fragment for destruída.
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

    /**
     * Chamado pelo Android toda vez que a aba volta a aparecer na frente (inclusive na primeira
     * vez). Pede ao ViewModel para recarregar a lista — assim, ao voltar da tela de detalhes, uma
     * viagem que foi para o roteiro já some daqui.
     */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    /**
     * Desenha o estado atual: se deu erro, mostra a mensagem de erro no lugar da lista. Senão,
     * entrega a lista ao Adapter (que desenha cada item) e, se ela estiver vazia, mostra o aviso de
     * "nenhuma viagem". Tocar num item abre a [DetalhesViagemActivity], levando o id da viagem como
     * "extra" do Intent.
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

    /**
     * Chamado pelo Android quando a tela do fragment vai ser destruída (por exemplo, ao trocar de
     * aba). Solta o Adapter da lista para não segurar memória à toa.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        recyclerViagens.adapter = null
    }
}
