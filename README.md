<p align="center"><img src="assets/icon.png" width="128" alt="Nyx"></p>

# Nyx Loader

Mod loader client-side para Minecraft Java Edition. Em desenvolvimento.

**Versão atual: 0.0.11**: Minecraft **1.7.10**, **Java 21**, mods **Forge** e mods **Fabric** (Legacy Fabric) juntos, além dos mods do Nyx.

## Como funciona

Desde a 0.0.4 o Nyx tem lançador próprio e não usa mais o LaunchWrapper.

1. O launcher inicia `dev.nyx.loader.launch.NyxMain` com Java 21.
2. Na primeira vez, o Nyx remapeia o `client.jar` dos nomes ofuscados para os nomes **intermediary** do Legacy Fabric (`net.minecraft.class_1600`, `method_2921`...). O resultado fica em `<pasta do jogo>/.nyx/remapped/` e é reutilizado.
3. Um classloader próprio (`NyxClassLoader`) carrega o jogo remapeado e os mods. Cada classe passa por: patches do Nyx (ASM), access wideners, remoção de código `@Environment(SERVER)` e Mixin.
4. Patches do Nyx no jogo:
   - `class_1600.method_2921` (inicialização): entrypoints Fabric `main`/`client` antes da criação da janela e mods do Nyx no fim;
   - `class_1600.method_2954` (tick) e `method_6648` (antes de `Display.update()`, HUD, teclas e FPS);
   - `class_624` (menu principal): botão do ModMenu;
   - `ClientBrandRetriever`: marca `nyx`.

## Forge + Fabric juntos

Desde a 0.0.10, quando o Forge 1.7.10 (10.13.4.1614) está nas bibliotecas, o Nyx roda o **Forge original** e os mods Fabric na mesma partida:

- O Nyx tem uma implementação própria do LaunchWrapper (`net.minecraft.launchwrapper`) que funciona no Java 21 e executa o `FMLTweaker` do Forge.
- Correções aplicadas ao Forge durante o carregamento: sem `SecurityManager` (removido do Java 21), `EnumHelper` e object holders via `Unsafe`/MethodHandles, Pack200 do Commons Compress para os binpatches, adaptadores `RemappingClassAdapter`/`RemappingMethodAdapter` para o ASM 9, visitantes ASM5 promovidos para ASM9, emulação de `Field.modifiers` para mods antigos e campos `static final` do Minecraft liberados para reflexão.
- Com o Forge, o jogo usa os nomes SRG/MCP do Forge. Classes dos mods Fabric (e do próprio Nyx) são convertidas de intermediary para SRG ao carregar, incluindo strings de anotações e constantes, refmaps do Mixin e access wideners.
- Mixins Fabric que não encaixam no código alterado pelo Forge não derrubam o jogo: o injetor é ignorado e o handler não usado é removido. A lista `config/nyx/forge-disabled-mixins.txt` desliga mixins específicos.
- Módulos da Legacy Fabric API que substituem registros e rede (registry-sync, block, item, entity, biome, effect, enchantment, command, networking) não são carregados com o Forge, porque o FML já controla IDs e pacotes. Mods Fabric que dependem deles mostram o motivo no log.
- Sem o Forge nas bibliotecas, o Nyx volta ao modo só Fabric da 0.0.4 e avisa no log quando encontra um mod Forge.
- O ModMenu lista mods Forge (tag verde), Fabric (laranja) e Nyx (azul).
- Na primeira execução com Forge, o Nyx cria `config/splash.properties` com `enabled=false`, porque a tela de splash do Forge costuma travar em launchers Android.

## Novidades da 0.0.11

- **Cache de classes**: o resultado das transformações (Forge, OptiFine, remapeamento, patches do Nyx) fica em `.nyx/class-cache/` e é reaproveitado na próxima abertura. Classes alteradas por Mixin não entram no cache. O cache é descartado quando muda algum arquivo em `mods/`, o classpath, a configuração em `config/` ou a versão do Nyx, e também quando a abertura anterior não chegou ao menu principal. Desligue com `class-cache=false` em `config/nyx/loader.properties`.
- **Recuperação de crash**: quando o jogo crasha no cliente, o Nyx salva o relatório em `crash-reports/`, aponta o provável mod culpado (pela pilha do erro e pelos handlers de Mixin) e mantém o jogo aberto. Dentro do mundo, a tela é fechada e a mensagem aparece no chat. Fora do mundo, ou se o servidor integrado crashar, o mundo é fechado e uma tela mostra o culpado e o trecho do erro. Se o crash envolver shaders do OptiFine (ou acontecer até 60 s depois de trocar de shader), o shader é desativado e salvo como `OFF`. Depois de 3 crashes em 30 s o Nyx desiste e o jogo fecha como antes. Desligue com `crash-recovery=false` em `config/nyx/loader.properties`.
- **Configurações no ModMenu**: ao passar o mouse no ícone de um mod que tem tela de configuração, o ícone escurece 25% e mostra três barras; clicar abre as configurações. O mod selecionado também ganha um botão no canto superior direito dos detalhes. Funciona com `guiFactory` do Forge, entrypoint `modmenu` de mods Fabric, OptiFine (abre as configurações de vídeo) e mods do Nyx.
- **Baixar mods do Modrinth**: o botão **Baixar mods** no ModMenu pesquisa mods com versão para 1.7.10 (Forge e Fabric/Legacy Fabric), baixa a versão mais nova com dependências obrigatórias e confere o SHA-1. Mods do Nyx sem mixins e mods Fabric só de cliente (apenas entrypoints `client`/`modmenu`, sem mixins, access widener ou jar-in-jar) carregam na hora, sem reiniciar. Mods Forge e mods com mixins pedem reinício.

## Compatibilidade com Fabric

- Lê `fabric.mod.json` (schema 1): entrypoints `preLaunch`, `main`, `client` (classe, `Classe::campo` ou `Classe::método`), `languageAdapters`, `mixins`, `accessWidener`, `jars` (jar-in-jar), `depends`/`recommends`/`suggests`/`breaks`/`conflicts`, `provides`, `environment`, `icon`, `contact`, `custom`.
- API `net.fabricmc.loader.api` (`FabricLoader`, `ModContainer`, `ModMetadata`, `Version`, `SemanticVersion`, `VersionPredicate`, `MappingResolver`, `ObjectShare`, entrypoints) e `net.fabricmc.api`. A implementação é do Nyx, com as mesmas assinaturas.
- Mixin: Fabric Mixin 0.17.5 (Mixin 0.8.7) e MixinExtras 0.5.5 (embutido no jar do Nyx). O Nyx se apresenta como `fabricloader` 0.20.1.
- Mods suportados: os feitos para **Minecraft 1.7.10 com Legacy Fabric** (mesmos nomes intermediary), incluindo a Legacy Fabric API. Mods Fabric de versões modernas não funcionam, porque as classes do jogo são outras.
- Se faltar uma dependência, o jogo não abre e o log mostra a lista dos problemas, por exemplo `keyyyyyyyy 3.2.0 requires java >=25, but found 21`.

## Criando um mod

Estrutura mínima do `.jar`:

```
nyx.mod.properties
seu/pacote/MeuMod.class
```

`nyx.mod.properties`:

```
id=meu-mod
name=Meu Mod
version=0.0.1
entrypoint=seu.pacote.MeuMod
```

`id` aceita apenas `a-z`, `0-9`, `_` e `-`. Todo o resto é opcional e aparece no ModMenu:

```
name=Meu Mod
version=0.0.1
description=O que o mod faz.
authors=Autor 1, Autor 2
license=MIT
website=https://exemplo.com
issues=https://exemplo.com/issues
icon=assets/meumod/icon.png
```

`icon` é o caminho de um PNG dentro do `.jar`.

```java
public class MeuMod implements NyxMod {

    @Override
    public void onInitialize() {
        Nyx.onClientTick(this::tick);
    }

    private void tick() {
    }
}
```

API do Nyx (`dev.nyx.api.Nyx`):

| Método | O que faz |
| --- | --- |
| `onClientTick(Runnable)` | roda a cada tick do cliente |
| `onHud(HudRenderer)` | desenha por cima do jogo a cada frame (`NyxGraphics`: `fillRect`, `drawString`, `stringWidth`, `width`, `height`, `fps`, `frameTimeMillis`, `inGame`, `screenOpen`) |
| `registerKey(modId, nome, tecla, Runnable)` | atalho de teclado; a tecla pode ser trocada em `config/nyx/keys.properties` |
| `getConfig(modId)` | `NyxConfig` em `config/nyx/<id>.properties` (`get`, `getInt`, `getBoolean`, `set`, `setDefault`, `save`) |
| `getFps()` | FPS atual |
| `getGameDirectory()`, `getModsDirectory()`, `getConfigDirectory()` | pastas |
| `isModLoaded(String)`, `log(String)` | utilidades |
| `onFrame(Runnable)` | roda no começo de cada frame, na thread de renderização |
| `onWorldJoin(Runnable)`, `onWorldLeave(Runnable)` | entrar e sair de um mundo |
| `onScreenChange(ScreenListener)` | troca de tela (recebe a anterior e a nova, ou `null`) |
| `runOnRenderThread(Runnable)`, `isRenderThread()` | executa na thread do OpenGL; use para qualquer chamada de renderização feita fora dela |
| `sendClientMessage(String)` | mensagem local no chat (aceita códigos `§`) |
| `getMinecraft()`, `getFrameTimeMillis()` | instância do jogo e tempo do último frame |
| `registerConfigScreen(modId, ConfigScreenFactory)` | tela de configuração própria (`create(parent)` devolve um `GuiScreen`) |

`NyxOptions` cria uma tela de configuração pronta, salva em `config/nyx/<id>.properties`:

```java
NyxOptions.of("meu-mod")
        .title("Meu Mod")
        .toggle("ativo", "Ativado", true)
        .cycle("modo", "Modo", "rapido", "rapido", "bonito")
        .slider("distancia", "Distância", 8, 2, 32)
        .onSave(this::recarregar)
        .register();
```

Para liberdade total, `nyx.mod.properties` aceita `mixins=` (configs Mixin separadas por vírgula) e `accessWidener=`, como um mod Fabric. As classes do Minecraft são as **intermediary** do Legacy Fabric (`net.minecraft.class_1600`...) e o Nyx converte para os nomes do Forge quando ele está presente.

Ações de teclas só disparam sem nenhuma tela aberta.

Classes do LWJGL 2 (`Display`, `Keyboard`, `Mouse`, `GL11`) podem ser usadas diretamente.

Dependências do mod (escopo `provided`): `dev.nyx:nyx-loader` e `org.lwjgl.lwjgl:lwjgl:2.9.1`. Veja `examples/`.

## ModMenu

O Nyx adiciona um botão com o ícone do osso no canto superior direito do menu principal. Dentro do jogo, **Shift direito** abre o mesmo menu. A tela mostra só os mods da pasta `mods` e tem barra de pesquisa, filtro (tudo, mods, bibliotecas), lista de mods à esquerda (rolada pela barra do meio) e os detalhes do mod selecionado à direita (ícone, versão, autores, descrição, licença, arquivo, Site, Issues e configurações). Nesta versão o botão de abrir a pasta e os links tentam o navegador do sistema; se ele não existir (Android), o endereço aparece na tela e é copiado para a área de transferência. A tela segue o idioma de `options.txt` (português ou inglês).

## Exemplos

- `examples/hello-nyx`: escreve no log e muda o título da janela.
- `examples/session-timer`: mostra o tempo de sessão no título da janela, atualizado pelo hook de tick.
- `examples/fps-counter`: mostra FPS e tempo do quadro no canto da tela com `onHud`. **F6** liga e desliga; a tela de configuração (`NyxOptions`) no ModMenu define o canto, o fundo e o tempo do quadro.
- `examples/log-probe`: cria `logs/nyx-probe.log` na pasta do jogo (versão, Java, pasta e ticks), útil para confirmar que o Nyx está rodando.
- `examples/fabric-example`: mod **Fabric** com `ClientModInitializer`, um Mixin no menu principal (`class_624.method_1025`) e um access widener (`class_1600.field_3787`, o FPS interno do jogo). Serve de modelo para mods Fabric no Nyx.

## Compilar

Requer JDK 21 ou superior, Maven e Python 3.

```
mvn package
bash scripts/package-release.sh 0.0.4
```

A saída fica em `dist/`. O GitHub Actions faz o mesmo a cada push e, ao enviar uma tag `v*`, publica um Release.

## Instalar no Fold Craft Launcher (FCL)

1. No FCL, instale o Minecraft **1.7.10** normal com o nome **Nyx-1.7.10** e abra-o uma vez.
2. Copie `Nyx-1.7.10.json` (pasta `dist/`, com Forge) ou `Nyx-1.7.10-fabric-only.json` (sem Forge) para `versions/Nyx-1.7.10/` com o nome `Nyx-1.7.10.json`.
3. Nas configurações da versão, escolha **Java 21**.
4. Coloque mods do Nyx e mods Fabric (Legacy Fabric 1.7.10) em `versions/Nyx-1.7.10/mods` (versão isolada).
5. O FCL baixa sozinho `nyx-loader`, ASM 9.8, Fabric Mixin, os mapeamentos intermediary 1.7.10 do Legacy Fabric e, no JSON com Forge, o Forge 10.13.4.1614 e as bibliotecas dele.
6. No log procure `[Nyx] Nyx Loader 0.0.4` e a lista de mods carregados.

O `Nyx-1.7.10.json` tem a versão vanilla no patch `game` e o Nyx no patch `nyx` (`mainClass` `dev.nyx.loader.launch.NyxMain`, `javaVersion` 21).

## Limitações da 0.0.11

- Somente Minecraft 1.7.10 e somente cliente.
- Forge: testado com Biomes O' Plenty 2.1.0 (mundo do tipo Biomes O' Plenty criado). Coremods que dependem de detalhes do LaunchWrapper original ou do Java 8 podem falhar; mods Forge que usam Mixin próprio (MixinTweaker) não têm o tweaker carregado.
- Precisa de Java 21 ou mais novo.
- Mods Fabric precisam ser feitos para 1.7.10 (Legacy Fabric). Mods para outras versões são recusados pelo `depends` ou falham ao carregar.
- Resolução de dependências simplificada: em cada id fica a maior versão compatível. Mods dentro de outros (jar-in-jar) que não servem para 1.7.10 são ignorados.
- Mods instalados pelo Modrinth que registram conteúdo, usam mixins ou são do Forge só carregam depois de reiniciar o Minecraft. Os ícones do Modrinth vêm em WebP, que o Java não lê, então a lista usa o ícone padrão.
- A recuperação de crash não cobre crashes durante a abertura do jogo nem `OutOfMemoryError`.
- Testado em Linux com Java 21: Legacy Fabric API 1.13.5, Omnilook, MenuFPSUnlocker, Exit Confirmation, Legacy Fixes e Perspective Nametag carregaram e um mundo foi criado. Em launchers Android ainda não foi testado.

## Licença

Veja `License.txt`.

## Biblioteca no repositório

O JSON do FCL baixa `nyx-loader` de `maven/dev/nyx/nyx-loader/<versão>/` (via `raw.githubusercontent.com`, branch `v<versão>`), então essas branches precisam continuar existindo.

## Bibliotecas de terceiros

- MixinExtras 0.5.5 (MIT), embutido em `META-INF/jars`, com a licença dentro do jar.
- Fabric Mixin, ASM e os mapeamentos intermediary do Legacy Fabric são baixados pelo launcher a partir dos repositórios oficiais; não são redistribuídos aqui.
