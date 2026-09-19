package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.CaminhaoEmRota
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.MapaViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.Marker
import com.google.android.gms.maps.model.MarkerOptions

/**
 * Mostra no mapa os caminhões com viagem em rota agora, atualizando sozinho: a ViewModel escuta
 * a coleção de viagens (addSnapshotListener) e entrega a lista já pronta aqui — a Fragment só
 * cuida de desenhar isso como Marker no GoogleMap.
 */
class MapaFragment : Fragment(R.layout.fragment_mapa), OnMapReadyCallback {

    companion object {
        // Presidente Getúlio - SC, onde fica a usina.
        private val LOCAL_PADRAO = LatLng(-27.05083, -49.62278)
        private const val ZOOM_PADRAO = 12f
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: MapaViewModel by viewModels()

    private var mapa: GoogleMap? = null
    private val marcadores = mutableMapOf<String, Marker>()
    private lateinit var textSemCaminhoes: TextView

    /** Pede o GoogleMap assíncrono e liga a ViewModel, que já começa a escutar o Firestore. */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textSemCaminhoes = view.findViewById(R.id.textSemCaminhoes)

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirCaminhoes(estado.caminhoes) }
        viewModel.escutar()
    }

    /** Chamado quando o GoogleMap termina de carregar; centraliza na usina e mostra dados pendentes. */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        mapa?.moveCamera(CameraUpdateFactory.newLatLngZoom(LOCAL_PADRAO, ZOOM_PADRAO))
        // O mapa pode ficar pronto depois de já termos recebido dados da ViewModel; sem isso,
        // aquela primeira atualização passaria batida (exibirCaminhoes ignora sem mapa pronto).
        viewModel.estado.value?.let { exibirCaminhoes(it.caminhoes) }
    }

    /** Cria/move um Marker por caminhão em rota e remove os que não estão mais na lista. */
    private fun exibirCaminhoes(caminhoes: List<CaminhaoEmRota>) {
        val mapaAtual = mapa ?: return
        val idsAtivos = caminhoes.map { it.viagemId }.toSet()

        for (caminhao in caminhoes) {
            val posicao = LatLng(caminhao.latitude, caminhao.longitude)
            val titulo = getString(R.string.mapa_marcador_formato, caminhao.motoristaNome, caminhao.caminhaoPlaca)

            val marcadorExistente = marcadores[caminhao.viagemId]
            if (marcadorExistente != null) {
                marcadorExistente.position = posicao
                marcadorExistente.title = titulo
            } else {
                val novoMarcador = mapaAtual.addMarker(MarkerOptions().position(posicao).title(titulo))
                if (novoMarcador != null) marcadores[caminhao.viagemId] = novoMarcador
            }
        }

        // Tira do mapa quem não está mais em rota (finalizou, cancelou ou não existe mais).
        val idsParaRemover = marcadores.keys - idsAtivos
        for (id in idsParaRemover) {
            marcadores.remove(id)?.remove()
        }

        textSemCaminhoes.visibility = if (marcadores.isEmpty()) View.VISIBLE else View.GONE
        ajustarCamera()
    }

    /** Com caminhões em rota, enquadra todos eles; sem nenhum, não mexe (fica no local padrão). */
    private fun ajustarCamera() {
        val posicoes = marcadores.values.map { it.position }
        if (posicoes.isEmpty()) return

        if (posicoes.size == 1) {
            mapa?.animateCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), ZOOM_PADRAO))
            return
        }

        val limites = LatLngBounds.Builder().apply {
            posicoes.forEach { include(it) }
        }.build()
        mapa?.animateCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
    }

    /** Solta as referências do mapa e dos marcadores; a view do mapa está sendo destruída. */
    override fun onDestroyView() {
        super.onDestroyView()
        marcadores.clear()
        mapa = null
    }
}
