package br.com.anderson.meuprimeiroprojeto.gerencial.backend

import br.com.anderson.meuprimeiroprojeto.gerencial.dados.PedidoComStatus
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.SaldoPedido
import br.com.anderson.meuprimeiroprojeto.gerencial.dados.StatusPedido
import com.google.firebase.Firebase
import com.google.firebase.firestore.firestore

/**
 * Cadastra e consulta pedidos no Firestore (coleção `pedidos`) e calcula a situação de cada um.
 *
 * A situação do pedido (pendente, em andamento, concluído) NÃO é gravada no banco: ela é
 * calculada na hora, a partir dos m³ reservados e das viagens (ver [calcularStatus]). Assim ela
 * nunca fica desatualizada.
 *
 * É um `object`: só existe uma cópia, usada direto pelo nome.
 */
object PedidoRepositorio {

    private val db get() = Firebase.firestore

    /**
     * Lista todos os pedidos, cada um já com a situação calculada, em ordem de data de entrega (a
     * mais próxima primeiro), para o gerente ver primeiro o que é mais urgente.
     *
     * Para decidir entre "em andamento" e "concluído", às vezes é preciso saber se o pedido ainda
     * tem viagem pendente; isso é uma consulta extra ao Firestore ([temViagemPendente]).
     */
    suspend fun listarComStatus(): List<PedidoComStatus> {
        val snapshot = db.collection("pedidos").get().aguardar()
        val pedidos = snapshot.documents.mapNotNull { documento ->
            val id = documento.id.toIntOrNull() ?: return@mapNotNull null
            val remoto = documento.toObject(PedidoRemoto::class.java) ?: return@mapNotNull null
            id to remoto
        }.sortedBy { it.second.dataEntrega }

        return pedidos.map { (id, remoto) ->
            val status = calcularStatus(remoto.m3Alocados, remoto.quantidadeTotalM3, temViagemPendente(id))
            PedidoComStatus(
                id, remoto.destinatarioNome, remoto.quantidadeTotalM3, remoto.m3Alocados, status, remoto.dataEntrega
            )
        }
    }

    /**
     * A regra da situação do pedido (ver [StatusPedido]):
     * - PENDENTE: nada foi reservado ainda;
     * - EM_ANDAMENTO: ainda sobra saldo, ou tudo foi reservado mas há viagem atribuída/em rota;
     * - CONCLUIDO: saldo zero e nenhuma viagem pendente.
     *
     * As contas usam [SaldoPedido], que arredonda para o litro. Sem isso, "sobrinhas" de
     * arredondamento do computador (0,7 + 0,1 + 0,1 + 0,1 = 0,9999999999999999) deixavam o pedido
     * em andamento para sempre.
     *
     * É uma "função pura": só faz contas com o que recebe, sem acessar banco nem internet. Por
     * isso fica separada: telas que já têm a lista de viagens em mãos (como o histórico do pedido)
     * usam esta função sem fazer outra consulta. E funções puras são as mais fáceis de testar.
     */
    fun calcularStatus(m3Alocados: Double, quantidadeTotalM3: Double, temViagemPendente: Boolean): StatusPedido {
        val saldo = SaldoPedido.calcular(quantidadeTotalM3, m3Alocados)
        return when {
            SaldoPedido.arredondar(m3Alocados) <= 0.0 -> StatusPedido.PENDENTE
            saldo > 0.0 -> StatusPedido.EM_ANDAMENTO
            temViagemPendente -> StatusPedido.EM_ANDAMENTO
            else -> StatusPedido.CONCLUIDO
        }
    }

    /** Busca um pedido pelo número; devolve `null` se não existir. */
    suspend fun buscarPorId(id: Int): PedidoRemoto? {
        val documento = db.collection("pedidos").document(id.toString()).get().aguardar()
        return documento.toObject(PedidoRemoto::class.java)
    }

    /**
     * Troca a data de entrega de um pedido já cadastrado. O `update` muda só esse campo; o resto do
     * pedido fica como estava.
     */
    suspend fun atualizarDataEntrega(pedidoId: Int, novaData: Long) {
        db.collection("pedidos").document(pedidoId.toString())
            .update("dataEntrega", novaData)
            .aguardar()
    }

    /**
     * Diz se o pedido tem alguma viagem ainda não resolvida (ATRIBUIDA ou EM_ROTA). Basta achar uma,
     * por isso o `limit(1)`: o Firestore para de procurar na primeira que encontrar.
     *
     * Observação: hoje esta função é chamada para TODOS os pedidos em [listarComStatus] (uma
     * consulta a mais por pedido), mesmo quando a resposta não muda a situação.
     */
    private suspend fun temViagemPendente(pedidoId: Int): Boolean {
        val snapshot = db.collection("viagens")
            .whereEqualTo("pedidoId", pedidoId)
            .whereIn("status", listOf("ATRIBUIDA", "EM_ROTA"))
            .limit(1)
            .get()
            .aguardar()
        return !snapshot.isEmpty
    }

    /**
     * Cadastra um pedido novo e devolve o número que ele recebeu.
     *
     * Os números são sequenciais (4, 5, 6...). Para isso existe um "contador" no documento
     * `contadores/pedidos`, com o último número usado. Tudo acontece numa transação: o Firestore lê
     * o contador, soma 1, grava o contador e o pedido novo juntos — e, se outro gerente estiver
     * cadastrando no mesmo instante, um dos dois repete a operação. Assim nunca saem dois pedidos
     * com o mesmo número.
     *
     * O contador começa em 3 (o primeiro pedido novo é o 4) porque os pedidos 1 a 3 já existiam,
     * criados pelo app do motorista na etapa 3 do projeto.
     */
    suspend fun cadastrar(
        destinatarioNome: String,
        documento: String,
        tipoPessoa: String,
        rua: String,
        numero: String,
        bairro: String,
        cidade: String,
        uf: String,
        cep: String,
        quantidadeTotalM3: Double,
        dataEntrega: Long,
        distanciaDedicadaKm: Double
    ): Int {
        val contadorRef = db.collection("contadores").document("pedidos")

        val novoId = db.runTransaction { transacao ->
            val snapshot = transacao.get(contadorRef)
            val atual = snapshot.getLong("proximo") ?: 3L
            val proximo = atual + 1

            transacao.set(contadorRef, mapOf("proximo" to proximo))
            transacao.set(
                db.collection("pedidos").document(proximo.toString()),
                PedidoRemoto(
                    destinatarioNome = destinatarioNome,
                    documento = documento,
                    tipoPessoa = tipoPessoa,
                    rua = rua,
                    numero = numero,
                    bairro = bairro,
                    cidade = cidade,
                    uf = uf,
                    cep = cep,
                    quantidadeTotalM3 = quantidadeTotalM3,
                    m3Alocados = 0.0,
                    dataEntrega = dataEntrega,
                    distanciaDedicadaKm = distanciaDedicadaKm
                )
            )
            proximo
        }.aguardar()

        return novoId.toInt()
    }
}
