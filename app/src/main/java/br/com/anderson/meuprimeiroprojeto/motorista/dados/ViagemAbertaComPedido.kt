package br.com.anderson.meuprimeiroprojeto.motorista.dados

import br.com.anderson.meuprimeiroprojeto.motorista.backend.ViagemRemota

/**
 * Uma linha da lista da aba "Viagens": junta a viagem com o pedido dela.
 *
 * A viagem e o pedido ficam em documentos separados no Firestore. Para a tela mostrar o nome do
 * destinatário e o endereço ao lado dos m³ da viagem, o `ViagensAbertasViewModel` busca os dois e
 * junta aqui.
 */
data class ViagemAbertaComPedido(
    /** Id do documento da viagem no Firestore (usado para abrir a tela de detalhes). */
    val viagemId: String,
    /** Os dados da viagem (m³, caminhão, situação...). */
    val viagem: ViagemRemota,
    /** O pedido da viagem, ou `null` se não foi possível buscá-lo (a viagem aparece mesmo assim). */
    val pedido: Pedido?
)
