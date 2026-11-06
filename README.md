# ConcretoTrack

Sistema para acompanhar em tempo real as entregas de concreto de uma usina com caminhões betoneira.
São dois apps Android (Kotlin, telas em XML) no mesmo projeto Gradle, com Firebase (Auth + Firestore):

- `:app`: app do motorista. Lista as viagens atribuídas a ele, agrupa viagens num roteiro, inicia o trânsito com rastreamento GPS e marca as entregas.
- `:gerencial`: app do gerente. Cadastra pedidos, motoristas e caminhões, atribui viagens e acompanha os caminhões no mapa e o histórico dos pedidos.

## Regras de negócio

| Regra | Descrição |
| --- | --- |
| RN-01 | Os números de identificação dos caminhões devem preservar os zeros à esquerda, conforme a convenção de identificação adotada por parte dos motoristas. |
