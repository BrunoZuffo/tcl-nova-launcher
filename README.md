# TCL Nova Launcher — v0.1

**Launcher Android TV próprio**, gratuito, sem anúncios, sem acesso root, sem telemetria e sem dependências de interface de terceiros. Criado como projeto inicial para TCL com **Android TV 9**. Não altera o firmware nem remove aplicativos do sistema.

## O que faz

- Tela inicial personalizada em vermelho/escuro, inspirada na proposta minimalista mostrada pelo usuário.
- Horário, data e saudação em português.
- Atalhos de Wi-Fi e configurações do Android.
- Descobre os aplicativos instalados e os abre pelo controle remoto.
- Exibe favoritos; pressionar OK por mais tempo no app alterna favoritos.
- Suporte a navegação por foco no controle, inclusive com realce.
- Declara-se como `HOME` e também como app `LEANBACK_LAUNCHER`, permitindo testes antes de alterar o launcher padrão.

## Pré-requisitos para compilar

- Android Studio recente **ou** Java 17 + Gradle 8.7 + Android SDK Platform 35 e Build Tools.
- Internet no **seu computador** para baixar os componentes oficiais do SDK e Gradle.
- TV Android 9 ou mais recente.

## Compilação pelo Android Studio

1. No computador, descompacte este ZIP.
2. Abra a pasta `TCL-Nova-Launcher` no Android Studio.
3. Aguarde a sincronização e a instalação do SDK 35, se solicitada.
4. Escolha **Build > Build Bundle(s) / APK(s) > Build APK(s)**.
5. APK gerado em `app/build/outputs/apk/debug/app-debug.apk`.

> Alternativa: envie os arquivos para um repositório GitHub privado ou público seu, e execute a Action `Gerar APK`. O workflow compila o APK e entrega como artefato. Uma execução em GitHub Actions não concede acesso à sua TV.

## Instalação segura na TV

1. **Mantenha o launcher original instalado** e o Projectivy como eventual fallback.
2. Transfira `app-debug.apk` para a TV, por pendrive ou via ADB previamente configurado.
3. Instale o APK (pode ser preciso autorizar a instalação para o gerenciador de arquivos).
4. Abra `TCL Nova` na lista de aplicativos e teste o controle remoto, Netflix e YouTube.
5. Somente depois teste selecionar `TCL Nova` como aplicativo Home padrão. A implementação da TCL pode manter um Home próprio, exigindo ajustes adicionais.
6. Se ocorrer algum problema, volte ao launcher original pelo menu Apps/Configurações da TV ou desinstale TCL Nova.

## Limitações honestas da versão 0.1

- Ainda **não foi compilada e testada em hardware TCL real** neste ambiente; este ZIP contém o projeto fonte, não um APK pronto.
- Não consegue listar recomendações de Netflix/YouTube nem reproduzir conteúdos dentro da tela inicial, pois isso requer integrações próprias.
- Desativar aplicativos preinstalados (`bloatware`) é tarefa distinta, a ser avaliada por lista de pacotes do modelo exato. **Nunca** use comandos genéricos para remover dezenas de pacotes.
- Alguns firmwares TCL podem ignorar a escolha do launcher HOME. Não desative o Home original sem ter caminho de recuperação testado.
- Esta versão não tem integração com dispositivos smart-home; pode ser implementada posteriormente.

## Segurança e privacidade

Sem permissão `INTERNET`, sem permissões privilegiadas, sem root, sem autenticação, sem ads e sem coleta de dados. O app não desativa ou desinstala outros apps.

## Estrutura

- `app/src/main/java/br/com/tclnova/launcher/MainActivity.java`: UI nativa, navegação e descoberta de apps
- `app/src/main/AndroidManifest.xml`: integração com launcher Android TV
- `app/src/main/res/drawable-nodpi`: ícone/banner
- `.github/workflows/build-apk.yml`: geração do APK no GitHub Actions
