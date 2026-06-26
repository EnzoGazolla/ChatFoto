# CEUB
## Ciência da Computação

## Integrantes:

Professor: Nikson Bernardes Fernandes Ferreira

- Enzo Gazolla Nagano, RA: 22401710
- Rodrigo Silva Madeira Campos, RA: 22400645
- Pedro Cezar Pires Henriques, RA: 22400985
- Bernando Gontijo Campello, RA: 22452856

# Chatfoto Bot

Este projeto é um bot do Telegram simples que utiliza a API do Gemini (Google AI) para responder mensagens de forma bem-humorada e amigável. O projeto foi construído utilizando Java e [jbang](https://api.jbang.dev/) para simplificar o gerenciamento de dependências e execução.

## Pré-requisitos

Para rodar este projeto, você precisará de:

*   **Java 21** ou superior.
*   **jbang**.
*   Um token de bot do Telegram (obtido com o [@BotFather](https://t.me/botfather)).
*   Uma chave de API do Gemini (obtida no [Google AI Studio](https://aistudio.google.com/)).

## Instalação

### 1. Instalando o Java e jbang (Recomendado via SDKMAN)

A forma mais fácil de instalar e gerenciar o Java e o jbang no Linux ou macOS é utilizando o **SDKMAN!**.

1.  Abra o terminal e instale o SDKMAN!:
    ```bash
    curl -s "https://get.sdkman.io" | bash
    ```
2.  Siga as instruções na tela e reinicie o terminal.
3.  Instale o Java 21:
    ```bash
    sdk install java 21.0.2-tem # Ou qualquer versão 21+ disponível
    ```
4.  Instale o jbang:
    ```bash
    sdk install jbang
    ```

### Alternativa: Instalação direta do jbang

Caso não queira usar o SDKMAN, você pode instalar o jbang diretamente:

*   **Linux/macOS:** `curl -Ls https://sh.jbang.dev | bash -s - app setup`
*   **Windows:** `iex "& { $(iwr -useb https://ps.jbang.dev) } app setup"`

## Configuração

O bot precisa de duas chaves de acesso: `TELEGRAM_BOT_TOKEN` e `GEMINI_API_KEY`. Você pode configurá-las como variáveis de ambiente ou passá-las como argumentos.

### Variáveis de Ambiente (Recomendado)

```bash
export TELEGRAM_BOT_TOKEN="seu_token_aqui"
export GEMINI_API_KEY="sua_chave_gemini_aqui"
```

## Como Executar.

Com o Java e o jbang instalados, você pode rodar o bot diretamente pelo terminal.

### Opção 1: Usando variáveis de ambiente

Se você já exportou as variáveis, basta rodar:

```bash
jbang Chatfoto.java
```

### Opção 2: Passando argumentos

```bash
jbang Chatfoto.java <TELEGRAM_BOT_TOKEN> <GEMINI_API_KEY>
```

O jbang irá baixar automaticamente todas as dependências necessárias e iniciar o bot. Quando vir a mensagem "Bot iniciado com sucesso!", seu bot estará pronto para responder no Telegram!

## Estrutura do Arquivo

O arquivo `Chatfoto.java` contém:
- Diretivas do jbang para Java 21 e dependências.
- Interface `Assistant` usando LangChain4j para integração com Gemini.
- Lógica do bot usando a API `java-telegram-bot-api`.

*
