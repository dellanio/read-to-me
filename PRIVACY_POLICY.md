# Política de Privacidade – ReadToMe

**Última atualização:** 6 de Novembro de 2026

O **ReadToMe** foi desenvolvido com um compromisso absoluto com a privacidade e o controle do usuário. Esta política descreve como o aplicativo opera, os dados aos quais tem acesso e como as informações são protegidas.

---

## 1. Uso do Serviço de Acessibilidade (`AccessibilityService`)

O ReadToMe utiliza a API `AccessibilityService` do Android com uma finalidade única, específica e sob demanda:
- **Finalidade:** Ler em voz alta o conteúdo de texto exibido na tela do usuário (mensagens, artigos, redes sociais e documentos) após um comando explícito (toque no botão flutuante ou seleção manual de uma área na tela).
- **Sob Demanda (On-Demand):** O aplicativo **não** monitora, não rastreia e não armazena nenhuma digitação, senha, dados biométricos, campos de senha ou atividades em segundo plano. Ele só examina os nós de texto visíveis no momento exato em que o usuário toca no botão de leitura.
- **Zero Gravação de Tela:** A captura de tela (quando necessária para OCR de imagens ou PDFs) é realizada exclusivamente na memória temporária (RAM) do aparelho e descartada imediatamente após o reconhecimento de texto.

---

## 2. Reconhecimento Óptico de Caracteres (OCR) no Dispositivo

Para telas que não possuem árvores de acessibilidade convencionais (como jogos, gráficos ou documentos escaneados), o aplicativo utiliza a biblioteca **Google ML Kit Text Recognition**:
- O processamento OCR ocorre **100% no próprio dispositivo** do usuário.
- Nenhuma imagem, print ou captura de tela é enviada para a nuvem para fins de OCR.

---

## 3. Chaves de API e Serviços de Terceiros (ElevenLabs, MiniMax e Gemini)

O ReadToMe permite que o usuário utilize suas próprias chaves de API para síntese de voz neural e resumos:
- **Armazenamento Seguro:** As chaves de API inseridas pelo usuário são armazenadas exclusivamente no dispositivo com criptografia via `EncryptedSharedPreferences`.
- **Transmissão de Texto:** Quando o usuário solicita a reprodução de um texto utilizando a ElevenLabs, o MiniMax ou resumos com o Google Gemini, o trecho de texto a ser lido é enviado diretamente do dispositivo para os endpoints oficiais da respectiva API via conexão criptografada (HTTPS/TLS).
- O desenvolvedor do ReadToMe não possui servidores intermediários e não intercepta, não visualiza e não armazena os textos ou chaves de API dos usuários.

---

## 4. Compartilhamento e Coleta de Dados

- **Sem Coleta de Dados Pessoais:** O aplicativo não coleta nome, e-mail, telefone, localização ou identificadores de publicidade (Ad ID).
- **Sem Rastreadores (Trackers):** O ReadToMe não inclui SDKs de analytics, publicidade ou rastreamento de terceiros.
- **Armazenamento Local:** Todas as preferências de voz, velocidade e visibilidade do botão são gravadas exclusivamente na memória local do smartphone.

---

## 5. Permissões Solicitadas e Justificativas

| Permissão | Finalidade |
| :--- | :--- |
| `BIND_ACCESSIBILITY_SERVICE` | Ler nós de texto e permitir a sobreposição do botão flutuante para controle da leitura. |
| `SYSTEM_ALERT_WINDOW` | Exibir o botão flutuante e o retículo de seleção na tela. |
| `INTERNET` | Comunicar-se diretamente com os serviços de TTS configurados (ElevenLabs / MiniMax) e baixar artigos web compartilhados pelo usuário. |
| `FOREGROUND_SERVICE` & `FOREGROUND_SERVICE_MEDIA_PLAYBACK` | Manter o áudio tocando em segundo plano quando a tela for bloqueada ou outro app for aberto. |

---

## 6. Contato e Dúvidas

Para qualquer dúvida sobre esta Política de Privacidade ou sobre o aplicativo, entre em contato através do repositório oficial no GitHub:
- **GitHub:** [https://github.com/dellanio/read-to-me](https://github.com/dellanio/read-to-me)
- **E-mail do Desenvolvedor:** dellanio@gmail.com
