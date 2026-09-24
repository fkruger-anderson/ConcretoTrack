package br.com.anderson.meuprimeiroprojeto.gerencial.backend

/**
 * Um caminhão do jeito que está gravado no Firestore, no documento `caminhoes/{placa}`.
 *
 * A placa não aparece aqui dentro porque ela é o próprio nome (id) do documento. O
 * `CaminhaoRepositorio` junta a placa com estes dados para montar um `Caminhao`.
 *
 * Os valores padrão existem porque o Firestore precisa criar o objeto vazio antes de preencher.
 */
data class CaminhaoRemoto(
    /** Fabricante, por exemplo "Mercedes-Benz". */
    val marca: String = "",
    /** Modelo, por exemplo "Atego 2426". */
    val modelo: String = "",
    /** Ano de fabricação. */
    val ano: Int = 0
)
