package br.com.alura.ecomart.chatbot.web.controller;

import br.com.alura.ecomart.chatbot.domain.service.ChatbotService;
import br.com.alura.ecomart.chatbot.web.dto.PerguntaDto;
import com.theokanning.openai.completion.chat.ChatCompletionChunk;
import io.reactivex.Flowable;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.ResponseBodyEmitter;

import java.util.Objects;

@Controller
@RequestMapping({"/", "chat"})
public class ChatController {

    private static final String PAGINA_CHAT = "chat";
    private ChatbotService chatbotService;

    public ChatController(ChatbotService chatbotService) {
        this.chatbotService = chatbotService;
    }

    @GetMapping
    public String carregarPaginaChatbot() {
        return PAGINA_CHAT;
    }

    @PostMapping
    @ResponseBody
    public ResponseBodyEmitter responderPergunta(@RequestBody PerguntaDto dto) {
        Flowable<ChatCompletionChunk> fluxoResposta = chatbotService.responderPergunta(dto.pergunta());
        ResponseBodyEmitter emitter = new ResponseBodyEmitter();

        fluxoResposta.subscribe(chunk -> {
                    String token = chunk.getChoices().get(0).getMessage().getContent();
                    if (Objects.nonNull(token)) {
                        emitter.send(token);
                    }
                }, emitter::completeWithError,
                emitter::complete);

        return emitter;
    }

    @GetMapping("limpar")
    public String limparConversa() {
        return PAGINA_CHAT;
    }

}
