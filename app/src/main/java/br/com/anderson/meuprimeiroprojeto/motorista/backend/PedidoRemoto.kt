package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Um pedido do jeito que está gravado no Firestore, no documento `pedidos/{id}`.
 *
 * O nome termina em "Remoto" para lembrar que é o formato da nuvem. As telas do motorista não
 * usam esta classe direto: o [PedidoRepositorio] a converte para `Pedido` (pacote `dados`), que
 * tem só o que o motorista precisa ver.
 *
 * O documento no Firestore tem mais campos do que esta classe: o CPF/CNPJ do destinatário (que
 * este app não pode ler, pela regra de privacidade LGPD), os m³ já reservados e a data de entrega
 * (que só interessam ao gerente). `@IgnoreExtraProperties` avisa o Firestore que deixar esses
 * campos de fora é de propósito; sem a anotação, ele deixaria um aviso no Logcat.
 *
 * Os valores padrão (`= ""`, `= 0.0`) existem porque o Firestore precisa criar o objeto vazio
 * antes de preencher os campos.
 */
@IgnoreExtraProperties
data class PedidoRemoto(
    /** Nome de quem vai receber o concreto. */
    val destinatarioNome: String = "",
    /** Endereço de entrega, em partes (rua, número, bairro, cidade). */
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
    /** Distância da usina até a obra (só a ida), usada para dividir os km de um roteiro. */
    val distanciaDedicadaKm: Double = 0.0
)
