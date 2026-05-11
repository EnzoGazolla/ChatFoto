///usr/bin/env jbang "$0" "$@" ; exit $?
//JAVA 21+
//DEPS com.github.pengrad:java-telegram-bot-api:7.1.1
//DEPS dev.langchain4j:langchain4j:0.35.0
//DEPS dev.langchain4j:langchain4j-google-ai-gemini:0.35.0
//DEPS org.slf4j:slf4j-simple:2.0.12

import com.pengrad.telegrambot.TelegramBot;
import com.pengrad.telegrambot.UpdatesListener;
import com.pengrad.telegrambot.model.Update;
import com.pengrad.telegrambot.request.SendMessage;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.SystemMessage;

public class Chatfoto {

    interface Assistant {
        @SystemMessage("""
            Você é um assistente prestativo e bem-humorado rodando dentro de um bot do Telegram.
            Suas respostas devem ser concisas e amigáveis.
            Sempre que possível, use emojis para tornar a conversa mais leve.
            """)
        String chat(String message);
    }

    private final TelegramBot bot;
    private final Assistant assistant;

    public Chatfoto(String botToken, String geminiKey) {
        this.bot = new TelegramBot(botToken);
        var model = GoogleAiGeminiChatModel.builder()
                .apiKey(geminiKey)
                .modelName("gemini-2.5-flash")
                .build();
        this.assistant = AiServices.create(Assistant.class, model);
    }

    public void start() {
        bot.setUpdatesListener(updates -> {
            updates.forEach(this::processUpdate);
            return UpdatesListener.CONFIRMED_UPDATES_ALL;
        });
        System.out.println("Bot iniciado com sucesso!");
    }

    private void processUpdate(Update update) {
        if (update.message() == null || update.message().text() == null) return;
        
        var chatId = update.message().chat().id();
        var text = update.message().text();
        
        try {
            String aiResponse = assistant.chat(text);
            bot.execute(new SendMessage(chatId, aiResponse));
        } catch (Exception e) {
            System.err.println("Erro ao processar mensagem: " + e.getMessage());
            bot.execute(new SendMessage(chatId, "Ops! 😵‍💫 Tive um erro: " + e.getMessage()));
        }
    }

    public static void main(String[] args) throws Exception {
        String botToken = System.getenv("TELEGRAM_BOT_TOKEN");
        String geminiKey = System.getenv("GEMINI_API_KEY");

        if (args.length >= 2) {
            botToken = args[0];
            geminiKey = args[1];
        }

        if (botToken == null || geminiKey == null) {
            System.err.println("Erro: TELEGRAM_BOT_TOKEN e GEMINI_API_KEY devem ser configurados.");
            System.err.println("Uso: jbang Chatfoto.java <BOT_TOKEN> <GEMINI_KEY>");
            System.exit(1);
        }

        new Chatfoto(botToken, geminiKey).start();
        
        // Mantém a aplicação rodando
        Thread.currentThread().join();
    }
}
