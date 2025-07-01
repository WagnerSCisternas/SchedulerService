package com.wasc.schedulerService.api;

import com.wasc.schedulerService.model.Task;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.time.LocalDate;
import java.util.List;

@Service
public class TaskApiClient {

    private final WebClient webClient;
    private final String baseUrl;
    private final String authUrl;
    private final String apiUsername;
    private final String apiPassword;

    // Token JWT que será armazenado e reutilizado (considere um mecanismo de expiração e renovação)
    private String jwtToken;

    public TaskApiClient(WebClient webClient,
                         @Value("${api.tasks.base-url}") String baseUrl,
                         @Value("${api.tasks.auth-url}") String authUrl,
                         @Value("${api.tasks.username}") String apiUsername,
                         @Value("${api.tasks.password}") String apiPassword) {
        this.webClient = webClient;
        this.baseUrl = baseUrl;
        this.authUrl = authUrl;
        this.apiUsername = apiUsername;
        this.apiPassword = apiPassword;
    }

    /**
     * Autentica na API de tarefas e obtém um token JWT.
     * Idealmente, este token deveria ser cacheado e renovado apenas quando expirar.
     */
    public String authenticate() {
        // Se já temos um token, podemos tentar usá-lo, ou sempre obter um novo
        // Para simplicidade, vamos obter um novo toda vez que for necessário.
        // Em produção, você implementaria lógica de cache e renovação.
        try {
            // Supondo que sua API de login espera um JSON com username e password
            String loginPayload = "{\"username\":\"" + apiUsername + "\",\"password\":\"" + apiPassword + "\"}";

            // Endpoint de login da sua API
            String fullAuthUrl = baseUrl + authUrl;

            // Faz a requisição POST para o endpoint de autenticação
            String tokenResponse = webClient.post()
                    .uri(fullAuthUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(loginPayload)
                    .retrieve()
                    .bodyToMono(String.class) // Ou um objeto DTO com o token
                    .block(); // Bloqueia para obter o resultado (usar Mono.subscribe para não bloquear)

            // Parse o token da resposta (isso depende de como sua API retorna o JWT)
            // Exemplo simples: Se a resposta for {"token": "seu.token.jwt"}
            if (tokenResponse != null && tokenResponse.contains("token")) {
                this.jwtToken = tokenResponse.substring(tokenResponse.indexOf(":") + 2, tokenResponse.lastIndexOf("\""));
                // Você pode usar uma biblioteca como Jackson para parsear o JSON de forma robusta
            } else {
                 this.jwtToken = tokenResponse; // Se a resposta for apenas o token puro
            }
            System.out.println("JWT Token obtido: " + jwtToken.substring(0, 20) + "..."); // Print parcial por segurança
            return this.jwtToken;

        } catch (Exception e) {
            System.err.println("Erro ao autenticar na API de tarefas: " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtém tarefas para um intervalo de datas.
     * @param jwtToken O token JWT para autenticação.
     * @param startDate Data de início do filtro.
     * @param endDate Data de fim do filtro.
     * @return Uma lista de tarefas.
     */
    public List<Task> getTasksByDueDateRange(String jwtToken, LocalDate startDate, LocalDate endDate) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            System.err.println("Token JWT ausente para buscar tarefas.");
            return List.of();
        }

        String url = String.format("%s/tasks?dueDateFrom=%s&dueDateTo=%s",
                                   baseUrl, startDate.toString(), endDate.toString());

        try {
            return webClient.get()
                    .uri(url)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .retrieve()
                    .bodyToFlux(Task.class) // Retorna um Flux de Task
                    .collectList()          // Coleta todos os itens em uma lista
                    .block();               // Bloqueia para obter o resultado
        } catch (Exception e) {
            System.err.println("Erro ao buscar tarefas da API: " + e.getMessage());
            return List.of();
        }
    }

    /**
     * Atualiza o status de alerta de uma tarefa na API.
     * Sua API de tarefas precisará ter um endpoint PATCH/PUT para isso.
     */
    public void updateTaskAlertStatus(Long taskId, boolean alertSentOneDayBefore, boolean alertSentOnDay, String jwtToken) {
        if (jwtToken == null || jwtToken.isEmpty()) {
            System.err.println("Token JWT ausente para atualizar status da tarefa.");
            return;
        }

        String updateUrl = String.format("%s/tasks/%d/updateAlertStatus", baseUrl, taskId);
        // Ou um endpoint mais genérico como /tasks/{id} com o corpo da requisição
        String updatePayload = String.format("{\"alertSentOneDayBefore\": %b, \"alertSentOnDay\": %b}",
                                             alertSentOneDayBefore, alertSentOnDay);

        try {
            webClient.put() // Ou patch, dependendo da sua API
                    .uri(updateUrl)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwtToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .bodyValue(updatePayload)
                    .retrieve()
                    .toBodilessEntity() // Não espera corpo na resposta, apenas status
                    .block();
            System.out.println("Status de alerta da tarefa " + taskId + " atualizado na API.");
        } catch (Exception e) {
            System.err.println("Erro ao atualizar status de alerta da tarefa " + taskId + ": " + e.getMessage());
        }
    }
}