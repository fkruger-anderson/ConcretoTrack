package br.com.anderson.meuprimeiroprojeto.motorista.ui

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import br.com.anderson.meuprimeiroprojeto.R
import br.com.anderson.meuprimeiroprojeto.motorista.backend.AutenticacaoRepositorio
import br.com.anderson.meuprimeiroprojeto.motorista.db.StatusRoteiro
import br.com.anderson.meuprimeiroprojeto.motorista.gps.RastreamentoService
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroUiState
import br.com.anderson.meuprimeiroprojeto.motorista.ui.viewmodel.RoteiroViewModel
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.PolylineOptions

/**
 * A aba "Roteiro": o roteiro atual do motorista.
 *
 * A tela muda conforme a situação:
 * - sem roteiro: só um aviso de que não há roteiro;
 * - roteiro PLANEJANDO: a lista das viagens e o botão "Iniciar roteiro";
 * - roteiro EM_ROTA: a lista (com botão "Entregue" em cada viagem pendente), o mapa com o
 *   trajeto até agora, o botão "Encerrar roteiro" e, se preciso, o aviso de bateria.
 *
 * Também é esta aba que pede as permissões de localização e notificação, e que religa o
 * rastreamento se o Android tiver matado o serviço de GPS.
 *
 * `OnMapReadyCallback` no cabeçalho quer dizer que esta classe "sabe responder" quando o mapa do
 * Google termina de carregar (função [onMapReady]).
 */
class RoteiroFragment : Fragment(R.layout.fragment_roteiro), OnMapReadyCallback {

    companion object {
        // Espaço (em pixels) deixado nas bordas ao enquadrar o trajeto no mapa.
        private const val PADDING_LIMITES_PX = 120

        // Mesmo intervalo do RastreamentoService — não faz sentido checar por pontos novos mais
        // rápido do que eles são gravados.
        private const val INTERVALO_ATUALIZACAO_MAPA_MS = 10_000L
    }

    private val viewModel: RoteiroViewModel by viewModels()

    private lateinit var textRoteiroVazio: TextView
    private lateinit var recyclerViagensRoteiro: RecyclerView
    private lateinit var botaoIniciarRoteiro: Button
    private lateinit var containerMapa: View
    private lateinit var botaoEncerrarRoteiro: Button
    private lateinit var textAvisoPermissao: TextView
    private lateinit var botaoAjustarBateria: Button
    private lateinit var textBateria: TextView
    // O mapa só existe depois que termina de carregar; até lá fica null.
    private var mapa: GoogleMap? = null
    // O último estado recebido do ViewModel, guardado para uso fora do exibirEstado.
    private var estadoAtual: RoteiroUiState = RoteiroUiState()

    /**
     * Enquanto a aba Roteiro fica aberta com o roteiro em rota, o `RastreamentoService` continua
     * gravando pontos novos no Room em segundo plano, mas nada mais avisa esta tela — sem isso, o
     * mapa fica congelado no trajeto que existia quando a aba foi aberta. Este `Handler` só
     * relê os pontos (local, via [RoteiroViewModel.atualizarPontos]) a cada
     * [INTERVALO_ATUALIZACAO_MAPA_MS], pra manter o desenho do trajeto acompanhando a viagem.
     *
     * Em palavras simples: o `Handler` é um "despertador". O `Runnable` é a tarefa que ele executa;
     * no fim da tarefa, ela mesma agenda o próximo despertar para daqui a 10 segundos, formando
     * uma repetição.
     */
    private val handlerAtualizacaoMapa = Handler(Looper.getMainLooper())
    private val atualizadorDeMapa = object : Runnable {
        /** A tarefa do "despertador": relê os pontos e agenda a si mesma de novo para daqui a 10 s. */
        override fun run() {
            viewModel.atualizarPontos()
            handlerAtualizacaoMapa.postDelayed(this, INTERVALO_ATUALIZACAO_MAPA_MS)
        }
    }

    /**
     * Pede a permissão de localização (e a de notificações, no Android 13 ou mais novo) para o
     * rastreamento poder funcionar.
     *
     * `registerForActivityResult` prepara o pedido de permissão e diz o que fazer com a resposta
     * do usuário (o bloco entre chaves). O pedido em si só aparece quando `.launch(...)` é chamado.
     * Se o usuário permitir e o roteiro já estava em rota (por exemplo, o serviço foi morto com o
     * app fechado), o rastreamento é religado na hora. Se negar, aparece um aviso na tela.
     */
    private val launcherPermissoesLocalizacao =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { resultados ->
            val localizacaoConcedida = resultados[Manifest.permission.ACCESS_FINE_LOCATION] == true
            if (localizacaoConcedida) {
                reasserirRastreamentoSeEmRota()
            } else {
                mostrarAvisoPermissaoNegada()
            }
        }

    /**
     * Pede a permissão de localização "o tempo todo" (em segundo plano), na hora de iniciar o
     * roteiro. Permitindo ou não, o roteiro é iniciado: sem ela o rastreamento ainda funciona com
     * o app aberto, só fica pior quando o motorista troca de app.
     */
    private val launcherPermissaoSegundoPlano =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) {
            viewModel.iniciarRoteiro()
        }

    /**
     * Chamado pelo Android logo depois que a tela do fragment foi montada (o
     * `Fragment(R.layout...)` do cabeçalho da classe já transformou o XML em tela; aqui só falta
     * encontrar os componentes pelo id).
     *
     * Também: liga os botões, pede o mapa do Google (ele carrega "por fora" e avisa em
     * [onMapReady]), começa a observar o ViewModel, pede as permissões e liga o "despertador" que
     * atualiza o mapa.
     */
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        textRoteiroVazio = view.findViewById(R.id.textRoteiroVazio)
        recyclerViagensRoteiro = view.findViewById(R.id.recyclerViagensRoteiro)
        recyclerViagensRoteiro.layoutManager = LinearLayoutManager(requireContext())
        botaoIniciarRoteiro = view.findViewById(R.id.botaoIniciarRoteiro)
        containerMapa = view.findViewById(R.id.containerMapa)
        botaoEncerrarRoteiro = view.findViewById(R.id.botaoEncerrarRoteiro)
        textAvisoPermissao = view.findViewById(R.id.textAvisoPermissao)
        botaoAjustarBateria = view.findViewById(R.id.botaoAjustarBateria)
        textBateria = view.findViewById(R.id.textBateria)

        botaoIniciarRoteiro.setOnClickListener { confirmarIniciarRoteiro() }
        botaoEncerrarRoteiro.setOnClickListener { confirmarEncerrarRoteiro() }

        val fragmentoMapa = childFragmentManager.findFragmentById(R.id.mapa) as SupportMapFragment
        fragmentoMapa.getMapAsync(this)

        viewModel.estado.observe(viewLifecycleOwner) { estado -> exibirEstado(estado) }

        pedirPermissaoDeLocalizacao()
        handlerAtualizacaoMapa.postDelayed(atualizadorDeMapa, INTERVALO_ATUALIZACAO_MAPA_MS)
    }

    /**
     * Chamado pelo Android toda vez que a aba volta a aparecer na frente (inclusive na primeira
     * vez). Recarrega o roteiro e confere de novo a otimização de bateria (o motorista pode ter
     * acabado de mudar essa configuração e voltado para o app).
     */
    override fun onResume() {
        super.onResume()
        val uid = AutenticacaoRepositorio.usuarioAtual?.uid ?: return
        viewModel.carregar(uid)
        verificarOtimizacaoDeBateria()
    }

    /**
     * Chamado quando o mapa do Google termina de carregar (ele carrega "por fora", sem travar a
     * tela). Antes disso não dá para desenhar nada; por isso o trajeto é desenhado aqui e, depois,
     * a cada atualização do estado.
     */
    override fun onMapReady(googleMap: GoogleMap) {
        mapa = googleMap
        desenharTrajeto(estadoAtual)
    }

    /**
     * Chamado pelo Android quando a tela do fragment vai ser destruída (por exemplo, ao trocar de
     * aba). Libera o que foi criado em [onViewCreated]: desliga o "despertador" do mapa e solta as
     * referências do mapa e da lista.
     */
    override fun onDestroyView() {
        super.onDestroyView()
        handlerAtualizacaoMapa.removeCallbacks(atualizadorDeMapa)
        mapa = null
        recyclerViagensRoteiro.adapter = null
    }

    /**
     * Redesenha a aba inteira a partir do estado do ViewModel.
     *
     * Sem roteiro: esconde tudo e mostra o aviso. Com roteiro: mostra a lista de viagens e alterna
     * entre os controles de "planejando" (botão Iniciar) e "em rota" (mapa, botão Encerrar, aviso
     * de bateria). Os botões ficam desligados enquanto uma operação está em andamento ou quando a
     * regra não permite (por exemplo, encerrar com viagem pendente).
     */
    private fun exibirEstado(estado: RoteiroUiState) {
        estadoAtual = estado
        if (!estado.carregado) return

        if (!estado.temRoteiro) {
            textRoteiroVazio.visibility = View.VISIBLE
            recyclerViagensRoteiro.visibility = View.GONE
            botaoIniciarRoteiro.visibility = View.GONE
            containerMapa.visibility = View.GONE
            botaoEncerrarRoteiro.visibility = View.GONE
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            return
        }

        textRoteiroVazio.visibility = View.GONE
        recyclerViagensRoteiro.visibility = View.VISIBLE
        recyclerViagensRoteiro.adapter = ViagemDoRoteiroAdapter(estado.viagens) { viagemId ->
            viewModel.marcarEntregue(viagemId)
        }

        val emRota = estado.status == StatusRoteiro.EM_ROTA.name
        botaoIniciarRoteiro.visibility = if (!emRota) View.VISIBLE else View.GONE
        botaoIniciarRoteiro.isEnabled = estado.podeIniciar && !estado.carregando
        containerMapa.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.visibility = if (emRota) View.VISIBLE else View.GONE
        botaoEncerrarRoteiro.isEnabled = estado.podeEncerrar && !estado.carregando

        if (emRota) {
            // Garante o rastreamento ativo mesmo se o serviço tiver sido morto pelo sistema
            // (bateria, memória) e o motorista reabrir o roteiro em rota: iniciar() é idempotente
            // (RastreamentoService só reinicia o GPS se ainda não estava ligado para este roteiro).
            reasserirRastreamentoSeEmRota()
            verificarOtimizacaoDeBateria()
        } else {
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
        }

        desenharTrajeto(estado)

        estado.erro?.getConteudoSeNaoTratado()?.let { mensagem ->
            Toast.makeText(requireContext(), mensagem, Toast.LENGTH_LONG).show()
        }
        estado.roteiroEncerrado?.getConteudoSeNaoTratado()?.let {
            Toast.makeText(requireContext(), R.string.roteiro_encerrado_sucesso, Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Desenha no mapa a linha do caminho percorrido até agora (uma "Polyline": uma linha que liga
     * vários pontos) e ajusta a câmera para mostrar o trajeto inteiro. Com um ponto só, apenas
     * centraliza nele. Só faz algo com o roteiro em rota e o mapa já carregado.
     */
    private fun desenharTrajeto(estado: RoteiroUiState) {
        val mapaAtual = mapa ?: return
        mapaAtual.clear()
        if (estado.status != StatusRoteiro.EM_ROTA.name || estado.pontos.isEmpty()) return

        val posicoes = estado.pontos.map { LatLng(it.latitude, it.longitude) }
        mapaAtual.addPolyline(PolylineOptions().addAll(posicoes).width(8f))
        if (posicoes.size == 1) {
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngZoom(posicoes.first(), 15f))
        } else {
            val limites = LatLngBounds.Builder().apply { posicoes.forEach { include(it) } }.build()
            mapaAtual.moveCamera(CameraUpdateFactory.newLatLngBounds(limites, PADDING_LIMITES_PX))
        }
    }

    /**
     * Mostra uma caixa de confirmação (`AlertDialog`) antes de iniciar o roteiro, porque iniciar
     * liga o GPS e não dá mais para mexer nas viagens. Se o motorista confirmar, segue para
     * [iniciarRoteiroComPermissoes].
     */
    private fun confirmarIniciarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_iniciar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> iniciarRoteiroComPermissoes() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    /**
     * Confere as permissões antes de iniciar o roteiro:
     * - sem permissão de localização: mostra o aviso e não inicia nada (o serviço de GPS se
     *   desligaria sozinho mesmo);
     * - com localização, mas sem "o tempo todo": pede essa permissão primeiro (o roteiro é
     *   iniciado depois da resposta, qualquer que seja);
     * - com tudo permitido: inicia direto.
     */
    private fun iniciarRoteiroComPermissoes() {
        val temLocalizacao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (!temLocalizacao) {
            mostrarAvisoPermissaoNegada()
            return
        }

        val temSegundoPlano = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        if (temSegundoPlano) {
            viewModel.iniciarRoteiro()
        } else {
            launcherPermissaoSegundoPlano.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    /**
     * Mostra uma caixa de confirmação antes de encerrar o roteiro (encerrar desliga o GPS e
     * calcula os km). Se o motorista confirmar, pede ao ViewModel para encerrar.
     */
    private fun confirmarEncerrarRoteiro() {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.roteiro_confirmar_encerrar)
            .setPositiveButton(R.string.cancelar_viagem_confirmar) { _, _ -> viewModel.encerrarRoteiro() }
            .setNegativeButton(R.string.cancelar_viagem_voltar, null)
            .show()
    }

    /** Mostra na tela o aviso de que, sem a permissão de localização, o roteiro não pode rastrear. */
    private fun mostrarAvisoPermissaoNegada() {
        textAvisoPermissao.apply {
            text = getString(R.string.andamento_permissao_negada)
            visibility = View.VISIBLE
        }
    }

    /**
     * Assim que a aba abre, pede a permissão de localização (precisa e aproximada), se ainda não
     * foi dada. No Android 13 ou mais novo (`TIRAMISU`), pede junto a permissão de notificações,
     * que é necessária para mostrar a notificação fixa do serviço de GPS.
     */
    private fun pedirPermissaoDeLocalizacao() {
        val jaTemLocalizacao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (jaTemLocalizacao) return

        val permissoes = mutableListOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissoes.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        launcherPermissoesLocalizacao.launch(permissoes.toTypedArray())
    }

    /**
     * Se o roteiro está em rota e há permissão de localização, (re)liga o serviço de GPS.
     *
     * Pode ser chamada quantas vezes for preciso: ligar um serviço que já está ligado não causa
     * problema. É isso que recupera o rastreamento se o Android matou o serviço (por bateria ou
     * memória) enquanto o app estava fechado.
     */
    private fun reasserirRastreamentoSeEmRota() {
        val roteiroId = estadoAtual.roteiroId ?: return
        if (estadoAtual.status != StatusRoteiro.EM_ROTA.name) return
        val temPermissao = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (temPermissao) {
            RastreamentoService.iniciar(requireContext(), roteiroId)
        }
    }

    /**
     * Para economizar bateria, o Android pode matar serviços que rodam em segundo plano — inclusive
     * o de GPS. Esta função mostra o botão "Ajustar bateria" enquanto o roteiro está em rota e o
     * app ainda não foi liberado dessa economia. O botão abre a tela do sistema onde o motorista
     * libera o app. Fora de rota (ou sem roteiro), o botão fica escondido.
     */
    private fun verificarOtimizacaoDeBateria() {
        if (estadoAtual.status != StatusRoteiro.EM_ROTA.name || !estadoAtual.temRoteiro) {
            botaoAjustarBateria.visibility = View.GONE
            textBateria.visibility = View.GONE
            return
        }
        val powerManager = requireContext().getSystemService(PowerManager::class.java)
        val ignorandoOtimizacao = powerManager.isIgnoringBatteryOptimizations(requireContext().packageName)
        botaoAjustarBateria.visibility = if (ignorandoOtimizacao) View.GONE else View.VISIBLE
        textBateria.visibility = botaoAjustarBateria.visibility
        botaoAjustarBateria.setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:${requireContext().packageName}")
                )
            )
        }
    }
}
