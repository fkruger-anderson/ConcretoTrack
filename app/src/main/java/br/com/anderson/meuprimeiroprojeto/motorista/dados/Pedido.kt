package br.com.anderson.meuprimeiroprojeto.motorista.dados

/**
 * Um pedido de concreto, na versão que as telas do motorista usam.
 *
 * O pedido é cadastrado pelo gerente e fica guardado no Firestore. O `PedidoRepositorio` lê o
 * documento de lá e monta este objeto só com o que o motorista precisa ver. O CPF/CNPJ do
 * destinatário não está aqui de propósito (regra de privacidade, LGPD).
 *
 * `data class`: classe feita só para carregar dados.
 */
data class Pedido(
    /** Número do pedido. */
    val id: Int,
    /** Nome de quem vai receber o concreto. */
    val destinatarioNome: String,
    /** Endereço da obra, em partes. */
    val rua: String,
    val numero: String,
    val bairro: String,
    val cidade: String,
    /** Sigla do estado, por exemplo "SC". */
    val uf: String,
    /** CEP só com números. */
    val cep: String,
    /** Quantidade total de concreto do pedido, em m³. */
    val quantidadeTotalM3: Double,
    /** Distância da usina até a obra (só a ida), usada para dividir os km do roteiro. */
    val distanciaDedicadaKm: Double
) {
    /**
     * Junta as partes do endereço numa linha só, para caber na lista.
     * Exemplo: "Rua das Flores, 120 - Centro, Presidente Getúlio/SC".
     *
     * O `$rua` dentro do texto é substituído pelo valor do campo `rua` (isso se chama "template
     * de string").
     */
    fun enderecoResumido(): String = "$rua, $numero - $bairro, $cidade/$uf"
}
