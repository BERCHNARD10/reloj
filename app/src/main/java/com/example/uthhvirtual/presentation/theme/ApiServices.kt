package com.example.uthhvirtual.presentation.theme
import com.example.uthhvirtual.presentation.ListModel
import com.example.uthhvirtual.presentation.LoginRequest
import com.example.uthhvirtual.presentation.LoginResponse
import retrofit2.http.Body

import retrofit2.http.GET
import retrofit2.http.POST

interface ApiServices {
    @GET("notificaciones.php")
    suspend fun getList(): ArrayList<ListModel>

    @POST("loginUser.php")
    suspend fun login(@Body loginRequest: LoginRequest): LoginResponse
}



