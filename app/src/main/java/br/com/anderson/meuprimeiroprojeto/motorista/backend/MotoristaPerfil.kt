package br.com.anderson.meuprimeiroprojeto.motorista.backend

import com.google.firebase.firestore.IgnoreExtraProperties

/**
 * Os dados do motorista guardados no Firestore, no documento `motoristas/{uid}`.
 *
 * O Firestore é o banco de dados na nuvem do Firebase (Google). Ele guarda "documentos"
 * (parecidos com uma ficha) dentro de "coleções" (parecidas com uma gaveta de fichas). Aqui a
 * gaveta é `motoristas` e cada ficha tem como nome o `uid`: o código único que o Firebase Auth
 * dá para cada conta de login.
 *
 * `data class` é uma classe feita só para carregar dados. O Kotlin cria sozinho algumas coisas
 * úteis para ela, como comparar dois objetos pelo conteúdo e fazer cópias com `copy()`.
 *
 * O documento também tem o CPF do motorista, mas este app não precisa dele, então o campo fica de
 * fora. `@IgnoreExtraProperties` avisa o Firestore que isso é de propósito (sem a anotação, ele
 * deixaria um aviso no Logcat).
 *
 * O valor padrão (`= ""`) existe porque o Firestore, ao transformar o documento em objeto,
 * primeiro cria um objeto "vazio" e só depois preenche os campos um por um.
 */
@IgnoreExtraProperties
data class MotoristaPerfil(
    /** Nome completo do motorista. */
    val nome: String = ""
)
