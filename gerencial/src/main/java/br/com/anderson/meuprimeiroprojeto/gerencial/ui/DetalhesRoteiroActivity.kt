package br.com.anderson.meuprimeiroprojeto.gerencial.ui

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.gerencial.R
import br.com.anderson.meuprimeiroprojeto.gerencial.formatarKm
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroUiState
import br.com.anderson.meuprimeiroprojeto.gerencial.ui.viewmodel.DetalhesRoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.maps.model.PolylineOptions
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * A tela de detalhe de um roteiro: um mapa com o trajeto inteiro que o caminhão fez e a tabela de
 * "rateio" — como os km rodados foram divididos entre os pedidos que ele levou.
 *
 * `OnMapReadyCallback` no cabeçalho quer dizer que esta classe sabe responder quando o mapa do
 * Google termina de carregar ([onMapReady]).
 */
class DetalhesRoteiroActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        /** Nome do "extra" do Intent que leva o id do roteiro a mostrar. */
        const val EXTRA_ROTEIRO_ID = "roteiro_id"
        /** Nome do "extra" (opcional) com a viagem a destacar na tabela — a que o gerente tocou. */
        const val EXTRA_VIAGEM_DESTACADA_ID = "viagem_destacada_id"
        // Espaço (em pixels) deixado nas bordas ao enquadrar o trajeto no mapa.
        private const val PADDING_LIMITES_PX = 120
    }

    private val viewModel: DetalhesRoteiroViewModel by viewModels()

    // O mapa só existe depois que termina de carregar; até lá fica null.
    private var mapa: GoogleMap? = null
    private lateinit var roteiroId: String
    private lateinit var textSemPontos: TextView
    private lateinit var textMotorista: TextView
    private lateinit var textCaminhao: TextView
    private lateinit var textPeriodo: TextView
    private lateinit var textKmTotal: TextView
    private lateinit var recyclerRateio: RecyclerView
    // Formato de data e hora, por exemplo "21/09/2026 14:35".
    private val formatoData = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("pt-BR"))

    /**
     * Chamado pelo Android quando a tela é criada. Lê o id do roteiro que veio no Intent (sem ele a
     * tela fecha) e, se veio, a viagem a destacar. Liga os componentes e a lista da tabela, pede o
     * mapa do Google (que carrega "por fora" e avisa em [onMapReady]) e manda o ViewModel carregar.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_detalhes_roteiro)
        title = getString(R.string.detalhes_roteiro_titulo)

        val idRecebido = intent.getStringExtra(EXTRA_ROTEIRO_ID)
        if (idRecebido.isNullOrBlank()) {
            finish()
            return
        }
        roteiroId = idRecebido
        val viagemDestacadaId = intent.getStringExtra(EXTRA_VIAGEM_DESTACADA_ID)

        textSemPontos = findViewById(R.id.textSemPontos)
        textMotorista = findViewById(R.id.textMotorista)
        textCaminhao = findViewById(R.id.textCaminhao)
        textPeriodo = findViewById(R.id.textPeriodo)
        textKmTotal = findViewById(R.id.textKmTotal)
        recyclerRateio = findViewById(R.id.recyclerRateio)
        recyclerRateio.layoutManager = LinearLayoutManager(this)

        val fragmentoMapa = supportFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(this) { estado -> exibirEstado(estado) }
        viewModel.carregar(roteiroId, viagemDestacadaId)
    }

    /**
     * Chamado quando o mapa do Google termina de carregar. Guarda o mapa e redesenha o estado atual:
     * se os dados chegaram antes do mapa ficar pronto, o trajeto ainda não tinha sido desenhado.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        viewModel.estado.value?.let { exibirEstado(it) }
    }

    /**
     * Desenha o estado atual:
     * - textos de motorista, caminhão, período (início, e fim se já encerrado) e km total;
     * - a tabela de rateio (pelo [ItemRateioAdapter]);
     * - se o mapa já carregou: a linha do trajeto (Polyline), um marcador verde no início e um
     *   vermelho no fim, e a câmera enquadrando o trajeto todo. Sem pontos, mostra um aviso.
     */
    private fun exibirEstado(estado: DetalhesRoteiroUiState) {
        if (!estado.carregado) return

        textMotorista.text = getString(R.string.detalhes_roteiro_motorista_formato, estado.motoristaNome)
        textCaminhao.text = getString(R.string.detalhes_roteiro_caminhao_formato, estado.caminhaoPlaca)

        val inicio = estado.dataInicio?.let { formatoData.format(Date(it)) } ?: ""
        val fim = estado.dataFim
        textPeriodo.text = if (fim != null) {
            getString(R.string.detalhes_roteiro_periodo_formato, inicio, formatoData.format(Date(fim)))
        } else {
            getString(R.string.detalhes_roteiro_inicio_formato, inicio)
        }

        if (estado.kmTotalRota != null) {
            textKmTotal.text = getString(R.string.detalhes_roteiro_km_total_formato, formatarKm(estado.kmTotalRota))
            textKmTotal.visibility = View.VISIBLE
        } else {
            textKmTotal.visibility = View.GONE
        }

        recyclerRateio.adapter = ItemRateioAdapter(estado.itens)

        val mapaAtual = mapa ?: return

        if (estado.pontos.isEmpty()) {
            textSemPontos.visibility = View.VISIBLE
            return
        }
        textSemPontos.visibility = View.GONE

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
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }
}
