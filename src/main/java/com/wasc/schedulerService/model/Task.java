package com.wasc.schedulerService.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Data // Gera getters, setters, equals, hashCode e toString
@NoArgsConstructor // Gera um construtor sem argumentos
@AllArgsConstructor // Gera um construtor com todos os argumentos
public class Task {

    private Long id;
    private String title;
    private String description;
    private LocalDate dueDate;
    private String userEmail;
    private String userName;
    private String userPhoneNumber; // Para WhatsApp
    private boolean alertSentOneDayBefore; // Para controlar se o alerta de "um dia antes" foi enviado
    private boolean alertSentOnDay;        // Para controlar se o alerta de "no dia" foi enviado

}