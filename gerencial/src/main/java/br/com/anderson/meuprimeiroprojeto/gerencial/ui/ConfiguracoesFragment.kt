package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.ConfiguracoesViewModel

/** Aba "Configurações": cadastro de motoristas/caminhões e sair. */
class ConfiguracoesFragment : Fragment(R.layout.fragment_configuracoes) {

    private val viewModel: ConfiguracoesViewModel by viewModels()

    /** Mostra a saudação com o nome do gerente e liga os botões de cadastro e de sair. */
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
            AutenticacaoRepositorio.sair()
            startActivity(Intent(requireContext(), LoginActivity::class.java))
            requireActivity().finish()
        }
    }
}
