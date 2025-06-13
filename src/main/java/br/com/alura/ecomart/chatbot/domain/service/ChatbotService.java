package br.com.alura.ecomart.chatbot.domain.service;

import br.com.alura.ecomart.chatbot.infra.openai.DadosRequisicaoChatCompletion;
import br.com.alura.ecomart.chatbot.infra.openai.OpenAIClient;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import org.springframework.stereotype.Service;

@Service
public class ChatbotService {

    private OpenAIClient openAIClient;

    public ChatbotService(OpenAIClient openAIClient) {
        this.openAIClient = openAIClient;
    }

    public Flowable<ChatCompletionChunk> responderPergunta(String pergunta) {

        String prompSistema = "Você é um chatbot de atendimento a clientes de um ecommerce e deve responder apenas perguntas relacionadas com o ecommerce";
        DadosRequisicaoChatCompletion dados = new DadosRequisicaoChatCompletion(prompSistema, pergunta);
        return openAIClient.enviarRequisicaoChatCompletion(dados);
    }
}
