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
 * A aba "Mapa": mostra os caminhões na rua agora, e eles se mexem sozinhos na tela.
 *
 * Quem fica "escutando" as posições no Firestore é o [MapaViewModel] (ele escuta os roteiros em
 * rota). Toda vez que um caminhão anda, o ViewModel entrega a lista nova e esta tela só desenha:
 * cria, move ou apaga os marcadores (`Marker`, os "alfinetes" do mapa).
 *
 * `OnMapReadyCallback` no cabeçalho quer dizer que esta classe sabe responder quando o mapa do
 * Google termina de carregar ([onMapReady]).
 */
class MapaFragment : Fragment(R.layout.fragment_mapa), OnMapReadyCallback {

    companion object {
        // Presidente Getúlio - SC, onde fica a usina.
        private val LOCAL_PADRAO = LatLng(-27.05083, -49.62278)
        // Nível de aproximação do mapa (quanto maior, mais perto).
        private const val ZOOM_PADRAO = 12f
        // Espaço (em pixels) deixado nas bordas ao enquadrar vários caminhões.
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: MapaViewModel by viewModels()

    // O mapa só existe depois que termina de carregar; até lá fica null.
    private var mapa: GoogleMap? = null
    // Um marcador por roteiro (a chave é o id do roteiro), para mover o marcador certo depois.
    private val marcadores = mutableMapOf<String, Marker>()
    private lateinit var textSemCaminhoes: TextView

    /**
     * Chamado pelo Android logo depois que a tela do fragment foi montada. Pede o mapa do Google
     * (ele carrega "por fora", sem travar a tela, e avisa em [onMapReady]), começa a observar o
     * ViewModel e manda o ViewModel começar a escutar o Firestore.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        textSemCaminhoes = view.findViewById(R.id.textSemCaminhoes)

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirCaminhoes(estado.caminhoes) }
        viewModel.escutar()
    }

    /**
     * Chamado quando o mapa do Google termina de carregar. Centraliza na usina e desenha os
     * caminhões que já tinham chegado antes do mapa ficar pronto.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        mapa?.moveCamera(CameraUpdateFactory.newLatLngZoom(LOCAL_PADRAO, ZOOM_PADRAO))
        // O mapa pode ficar pronto depois de já termos recebido dados da ViewModel; sem isso,
        // aquela primeira atualização passaria batida (exibirCaminhoes ignora sem mapa pronto).
        viewModel.estado.value?.let { exibirCaminhoes(it.caminhoes) }
    }

    /**
     * Atualiza os marcadores do mapa a partir da lista recebida:
     * - caminhão que já tinha marcador: o marcador é movido para a posição nova;
     * - caminhão novo: um marcador é criado;
     * - marcador de caminhão que não está mais na lista (roteiro encerrado): é apagado.
     * No fim, mostra o aviso "nenhum caminhão em rota" se não sobrou nenhum, e ajusta a câmera.
     */
    private fun exibirCaminhoes(caminhoes: List<CaminhaoEmRota>) {
        val mapaAtual = mapa ?: return
        val idsAtivos = caminhoes.map { it.roteiroId }.toSet()

        for (caminhao in caminhoes) {
            val posicao = LatLng(caminhao.latitude, caminhao.longitude)
            val titulo = getString(R.string.mapa_marcador_formato, caminhao.motoristaNome, caminhao.caminhaoPlaca)

            val marcadorExistente = marcadores[caminhao.roteiroId]
            if (marcadorExistente != null) {
                marcadorExistente.position = posicao
                marcadorExistente.title = titulo
            } else {
                val novoMarcador = mapaAtual.addMarker(MarkerOptions().position(posicao).title(titulo))
                if (novoMarcador != null) marcadores[caminhao.roteiroId] = novoMarcador
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

    /**
     * Move a "câmera" (a parte do mapa que aparece na tela): com um caminhão só, centraliza nele;
     * com vários, enquadra todos. Sem nenhum, não mexe (fica na usina). `animateCamera` faz o
     * movimento de forma suave.
     */
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

    /**
     * Chamado pelo Android quando a tela do fragment vai ser destruída (por exemplo, ao trocar de
     * aba). Solta as referências do mapa e dos marcadores, que pertencem à tela que está saindo.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        marcadores.clear()
        mapa = null
    }
}
