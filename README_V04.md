# TCL Nova 0.4 — Interface vermelha premium + foto pelo celular

## Mudanças da 0.4

- Tema vermelho/preto como padrão; grafite e azul noturno disponíveis.
- Horário/data à esquerda, controles à direita e nome do app sob foco.
- Aplicativos em cartões grandes; usa banners de Android TV quando disponíveis.
- Foco com borda branca em todos os controles; acompanha o foco com rolagem.
- Favoritos por segurar OK: adicionar, remover e mover esquerda/direita.
- Aplicativos comuns e utilitários de sistema em seções separadas.
- Enviar papel de parede pelo celular na mesma rede usando QR Code, sem app adicional.
- Upload local com token aleatório, expira após 3 minutos, limite de 6 MB; requer JPG ou PNG.
- Foto é reduzida antes de salvar; pré-visualização antes de aplicar; decode do wallpaper em thread auxiliar.
- Nenhum serviço de rede permanece rodando quando a janela de upload é fechada.

## Instalação no projeto atual (Windows / PowerShell)

Abra `C:\Users\bruno\Downloads\TCL-Nova-Launcher` no VS Code e abra o terminal.
Coloque o ZIP `TCL-Nova-v04-overlay.zip` na pasta Downloads e execute:

```powershell
Expand-Archive -Path "$env:USERPROFILE\Downloads\TCL-Nova-v04-overlay.zip" -DestinationPath . -Force
git status
git add app README_V04.md diagnostico_tcl.ps1
git commit -m "TCL Nova 0.4 - nova interface e wallpaper por celular"
git push origin main
```

No GitHub Actions verifique se `Gerar APK` ficou verde; baixe `TCL-Nova-APK` do run novo, extraia `app-debug.apk` e instale pela TV.

**Atenção:** esta versão não foi compilada com Android SDK nem testada em hardware real neste ambiente. A validação Java feita foi somente sintática. O GitHub Actions é o teste de compilação; os testes de foco, imagem e controle são feitos na TV.

**Assinatura do APK:** GitHub Actions pode gerar certificado de debug diferente de uma execução para outra. Se ocorrer conflito de assinatura, NÃO desinstale o launcher que estiver como Home padrão; primeiro configure e valide um launcher de recuperação.

## Enviar papel de parede

1. Na TV, abra TCL Nova, selecione engrenagem e depois `Receber papel de parede do celular`.
2. No celular conectado ao mesmo Wi-Fi, escaneie o QR Code. A página é local (HTTP, não HTTPS), por isso use apenas sua rede doméstica confiável.
3. Envie JPG ou PNG com até 6 MB; confirme a prévia na televisão.
4. Para voltar ao vermelho padrão, escolha `Remover papel de parede`.

**Limitações:** HEIC/HEIF do iPhone precisa ser convertido para JPEG/PNG; o botão Entradas é experimental em firmwares TCL, podendo exigir o botão físico do controle TCL; a biblioteca normal considera alguns apps do sistema como comuns para manter streaming disponível.

## Recuperação e debloat

Esta versão NÃO desativa ou desinstala pacotes Android. Não desative `com.google.android.tvlauncher` antes de comprovar que o botão Home abre o TCL Nova após reinicialização e que é possível retornar ao launcher original. Para o diagnóstico ADB, use o script `diagnostico_tcl.ps1` incluído no ZIP **apenas após confirmar que o APK funciona**. A análise é somente leitura, não desativa nada.

## Check-list mínimo de testes na TV

- [ ] Abre pelo ícone TCL Nova sem travar.
- [ ] Setas alcançam todos os controles e cada controle exibe borda branca.
- [ ] Seleção nunca fica cortada nas bordas; biblioteca e favoritos rolam junto.
- [ ] Nome do app selecionado muda sem recarregar a tela.
- [ ] Netflix / YouTube / Prime Video abrem e o controle Voltar retorna.
- [ ] Longo OK adiciona/remove/reordena favoritos e o estado persiste.
- [ ] Biblioteca não duplica favoritos; utilitários ficam na seção avançada.
- [ ] Celular acessa QR; JPEG/PNG é pré-visualizado e aplicado corretamente.
- [ ] Transferência para quando cancela; imagem anterior não é alterada até confirmar.
- [ ] TV reinicia sem perder a possibilidade de recuperar a tela inicial anterior.
