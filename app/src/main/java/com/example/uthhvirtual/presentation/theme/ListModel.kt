package com.example.uthhvirtual.presentation

data class ListModel (
    var intClvNotification: Int,
    var vchmensaje: String,
    var metadata: String,
    var fechaActualizacion: String,
)

data class Metadata(
    val docente: String,
    val asignatura: String,
    val fecha_entrega: String,
    val puntuacion: String,
    val calificacion: String
)

data class LoginResponse
    (
    val done: Boolean,
    val message: String?,
            )

data class LoginRequest(
    val matriculaAlum: String,
    val password: String

)