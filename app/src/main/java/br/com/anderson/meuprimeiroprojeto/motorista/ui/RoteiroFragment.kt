package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.app.AlertDialog
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

/** Aba "Roteiro": roteiro atual do motorista — sem roteiro, planejando ou em rota. */
class RoteiroFragment : Fragment(R.layout.fragment_roteiro), OnMapReadyCallback {

    companion object {
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: RoteiroViewModel by viewModels()

    private lateinit var textRoteiroVazio: TextView
    private lateinit var recyclerViagensRoteiro: RecyclerView
    private lateinit var botaoIniciarRoteiro: Button
    private lateinit var containerMapa: View
    private lateinit var botaoEncerrarRoteiro: Button
    private var mapa: GoogleMap? = null
    private var estadoAtual: RoteiroUiState = RoteiroUiState()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textRoteiroVazio = view.findViewById(R.id.textRoteiroVazio)
        recyclerViagensRoteiro = view.findViewById(R.id.recyclerViagensRoteiro)
        recyclerViagensRoteiro.layoutManager = LinearLayoutManager(requireContext())
        botaoIniciarRoteiro = view.findViewById(R.id.botaoIniciarRoteiro)
        containerMapa = view.findViewById(R.id.containerMapa)
        botaoEncerrarRoteiro = view.findViewById(R.id.botaoEncerrarRoteiro)

        botaoIniciarRoteiro.setOnClickListener { confirmarIniciarRoteiro() }
        botaoEncerrarRoteiro.setOnClickListener { confirmarEncerrarRoteiro() }

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }
    }

    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        desenharTrajeto(estadoAtual)
    }

    private fun exibirEstado(estado: RoteiroUiState) {
        estadoAtual = estado
        if (!estado.carregado) return

        if (!estado.temRoteiro) {
            textRoteiroVazio.visibility = View.VISIBLE
            recyclerViagensRoteiro.visibility = View.GONE
            botaoIniciarRoteiro.visibility = View.GONE
            containerMapa.visibility = View.GONE
            botaoEncerrarRoteiro.visibility = View.GONE
            return
        }

        textRoteiroVazio.visibility = View.GONE
        recyclerViagensRoteiro.visibility = View.VISIBLE
        recyclerViagensRoteiro.adapter = ViagemDoRoteiroAdapter(estado.viagens) { viagemId ->
            viewModel.marcarEntregue(viagemId)
        }

        val emRota = estado.status == StatusRoteiro.EM_ROTA.name
        botaoIniciarRoteiro.visibility = if (!emRota) View.VISIBLE else View.GONE
        botaoIniciarRoteiro.isEnabled = estado.podeIniciar
        containerMapa.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.isEnabled = estado.podeEncerrar

        desenharTrajeto(estado)

        estado.erro?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
        }
        estado.roteiroEncerrado?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(requireContext(), R.string.roteiro_encerrado_sucesso, Toast.LENGTH_SHORT).show()
        }
    }

    /** Desenha a Polyline do trajeto percorrido até agora (só faz sentido com o roteiro em rota). */
    private fun desenharTrajeto(estado: RoteiroUiState) {
        val mapaAtual = mapa ?: return
        if (estado.status != StatusRoteiro.EM_ROTA.name || estado.pontos.isEmpty()) return

        mapaAtual.clear()
        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }

    private fun confirmarIniciarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_iniciar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.iniciarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    private fun confirmarEncerrarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_encerrar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.encerrarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }
}
