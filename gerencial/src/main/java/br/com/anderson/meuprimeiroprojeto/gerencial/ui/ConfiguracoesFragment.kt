package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ConfiguracoesViewModel

/**
 * A aba "Configurações": saudação com o nome do gerente, botões para cadastrar motoristas,
 * caminhões e outros gerentes, e o botão Sair.
 *
 * Um `Fragment` é um pedaço de tela dentro da [MainActivity]; o layout é indicado no cabeçalho da
 * classe e montado pelo Android.
 */
class ConfiguracoesFragment : Fragment(R.layout.fragment_configuracoes) {

    private val viewModel: ConfiguracoesViewModel by viewModels()

    /**
     * Chamado pelo Android logo depois que a tela do fragment foi montada.
     *
     * Observa o ViewModel para mostrar "Olá, <nome>", pede para ele buscar o nome, e liga os botões:
     * cada botão de cadastro abre a tela correspondente com um `Intent`; o Sair pede ao ViewModel
     * para fazer o logout e então abre o login e fecha a tela atual (`requireActivity().finish()`).
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val textBoasVindas = view.findViewById<TextView>(R.id.textBoasVindas)
        viewModel.estado.observe(viewLifecycleOwner) { estado ->
            textBoasVindas.text = getString(R.string.home_boas_vindas, estado.nomeGerente)
        }
        viewModel.carregar()

        view.findViewById<Button>(R.id.botaoCadastrarMotorista).setOnClickListener {
            startActivity(Intent(requireContext(), CadastroMotoristaActivity::class.java))
        }

        view.findViewById<Button>(R.id.botaoCadastrarCaminhao).setOnClickListener {
            startActivity(Intent(requireContext(), CadastroCaminhaoActivity::class.java))
        }

        view.findViewById<Button>(R.id.botaoCadastrarGerente).setOnClickListener {
            startActivity(Intent(requireContext(), CadastroGerenteActivity::class.java))
        }

        view.findViewById<Button>(R.id.botaoSair).setOnClickListener {
            viewModel.sair()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
}
