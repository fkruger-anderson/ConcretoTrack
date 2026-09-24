package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Um pedido do jeito que está gravado no Firestore, no documento `pedidos/{id}` (o id é o número
 * do pedido).
 *
 * `documento` (CPF/CNPJ) e `tipoPessoa` só aparecem no app gerencial: o app do motorista nem lê
 * esses campos (regra de privacidade, LGPD).
 *
 * Os valores padrão existem porque o Firestore precisa criar o objeto vazio antes de preencher.
 */
data class PedidoRemoto(
    /** Nome de quem vai receber o concreto. */
    val destinatarioNome: String = "",
    /** CPF ou CNPJ do destinatário, só com números. */
    val documento: String = "",
    /** "FISICA" (CPF) ou "JURIDICA" (CNPJ), descoberto pela quantidade de dígitos do documento. */
    val tipoPessoa: String = "",
    /** Endereço da obra, em partes. */
    val rua: String = "",
    val numero: String = "",
    val bairro: String = "",
    val cidade: String = "",
    /** Sigla do estado, por exemplo "SC". */
    val uf: String = "",
    /** CEP só com números. */
    val cep: String = "",
    /** Quantidade total de concreto do pedido, em m³. */
    val quantidadeTotalM3: Double = 0.0,
    /**
     * Quantos m³ já estão reservados para viagens (atribuídas, em rota ou concluídas). O saldo do
     * pedido é o total menos este valor, calculado sempre por `SaldoPedido` (que arredonda para o
     * litro). Este número só muda dentro de transações (ao atribuir ou cancelar uma viagem), para
     * nunca ficar errado.
     */
    val m3Alocados: Double = 0.0,
    /** Data combinada para a entrega (milissegundos desde 1º/01/1970, à meia-noite do dia). */
    val dataEntrega: Long = 0,
    /** Distância da usina até a obra (só a ida), usada para dividir os km de um roteiro. */
    val distanciaDedicadaKm: Double = 0.0
)
