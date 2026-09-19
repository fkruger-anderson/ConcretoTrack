package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.RotaViagemUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.RotaViagemViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions

/** Desenha no mapa o trajeto (pontos de GPS) que o motorista percorreu numa viagem. */
class RotaViagemActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        /** Chave do Intent extra: id da viagem cujo trajeto será desenhado. */
        const val EXTRA_VIAGEM_ID = "viagem_id"
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: RotaViagemViewModel by viewModels()

    private var mapa: GoogleMap? = null
    private lateinit var viagemId: String
    private lateinit var textSemPontos: TextView

    /** Lê a viagem recebida por Intent, pede o GoogleMap assíncrono e manda carregar os pontos. */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_rota_viagem)
        title = getString(R.string.rota_titulo)

        val idRecebido = intent.getStringExtra(EXTRA_VIAGEM_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        viagemId = idRecebido

        textSemPontos = findViewById(R.id.textSemPontos)

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(viagemId)
    }

    /** Chamado quando o GoogleMap termina de carregar; desenha dados já recebidos, se houver. */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        // O mapa pode ficar pronto depois de já termos recebido os pontos da ViewModel; sem
        // isso, essa primeira atualização passaria batida (exibirEstado ignora sem mapa pronto).
        viewModel.estado.value?.let { exibirEstado(it) }
    }

    /** Desenha a Polyline do trajeto e os marcadores de início/fim a partir dos pontos de GPS. */
    private fun exibirEstado(estado: RotaViagemUiState) {
        if (!estado.carregado) return
        val mapaAtual = mapa ?: return

        if (estado.pontos.isEmpty()) {
            textSemPontos.visibility = View.VISIBLE
            return
        }

        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }

        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        mapaAtual.addMarker(
            MarkerOptions().position(posicoes.first())
                .title(getString(R.string.rota_marcador_inicio))
                .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN))
        )
        if (posicoes.size > 1) {
            mapaAtual.addMarker(
                MarkerOptions().position(posicoes.last())
                    .title(getString(R.string.rota_marcador_fim))
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED))
            )
        }

        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply {
                posicoes.forEach { include(it) }
            }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }
}
