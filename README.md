# Nyx Loader

Mod loader client-side para Minecraft Java Edition. Em desenvolvimento.

**Versão atual: 0.0.2** — suporta apenas o Minecraft **1.7.10**, com **Java 8**.

## Como funciona

O Nyx roda sobre o LaunchWrapper 1.12 (`net.minecraft.launchwrapper.Launch`), o mesmo mecanismo usado pelo Forge nessa versão.

1. O launcher inicia o jogo com `net.minecraft.launchwrapper.Launch` e `--tweakClass dev.nyx.loader.NyxTweaker`.
2. O `NyxTweaker` registra o transformador de bytecode, procura mods em `<pasta do jogo>/mods/*.jar` e repassa a execução para `net.minecraft.client.main.Main`.
3. O `NyxClassTransformer` injeta dois hooks na classe do jogo (`bao`) usando ASM:
   - fim da inicialização (`ag()V`), onde os mods são inicializados;
   - início de cada tick do cliente (`p()V`).
4. Se os métodos esperados não forem encontrados (outro build do jogo), o hook não é aplicado e um aviso aparece no log.

Os nomes ofuscados acima foram verificados no `client.jar` do Minecraft 1.7.10 (sha1 `e80d9b3bf5085002218d4be59e668bac718abbc6`).

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

`id` aceita apenas `a-z`, `0-9`, `_` e `-`. `name` e `version` são opcionais.

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

API da 0.0.2 (`dev.nyx.api.Nyx`): `onClientTick(Runnable)`, `getGameDirectory()`, `getModsDirectory()`, `isModLoaded(String)`, `log(String)`.

Classes do LWJGL 2 (`Display`, `Keyboard`, `Mouse`, `GL11`) podem ser usadas diretamente. Classes do Minecraft são ofuscadas nesta versão e ainda não há camada de mapeamentos.

Dependências do mod (escopo `provided`): `dev.nyx:nyx-loader` e `org.lwjgl.lwjgl:lwjgl:2.9.1`. Veja `examples/`.

## Exemplos

- `examples/hello-nyx`: escreve no log e muda o título da janela.
- `examples/session-timer`: mostra o tempo de sessão no título da janela, atualizado pelo hook de tick.
- `examples/log-probe`: cria `logs/nyx-probe.log` na pasta do jogo (versão, Java, pasta e ticks), útil para confirmar que o Nyx está rodando.

## Compilar

Requer JDK 17 ou superior, Maven e Python 3.

```
mvn package
bash scripts/package-release.sh 0.0.2
```

A saída fica em `dist/`. O GitHub Actions faz o mesmo a cada push e, ao enviar uma tag `v*`, publica um Release.

## Instalar no Fold Craft Launcher (FCL)

1. No FCL, instale o Minecraft **1.7.10** normal com o nome **Nyx-1.7.10** e abra-o uma vez.
2. Baixe `Nyx-0.0.2-install.zip` na página de Releases.
3. Extraia o zip na pasta do jogo do FCL (a que contém `versions` e `libraries`), mesclando as pastas e substituindo o `Nyx-1.7.10.json`.
4. Atualize a lista de versões, selecione **Nyx-1.7.10** e use Java 8.
5. Os mods de exemplo vão para `versions/Nyx-1.7.10/mods` (versão isolada). Sem isolamento, copie para `mods/` na raiz.
6. Procure linhas começando com `[Nyx]` no log, ou o arquivo `logs/nyx-probe.log`.

O `Nyx-1.7.10.json` usa o formato do FCL: a versão vanilla fica no patch `game` e o Nyx (LaunchWrapper, ASM e `nyx-loader`) no patch `nyx`, com `mainClass` `net.minecraft.launchwrapper.Launch` e `--tweakClass dev.nyx.loader.NyxTweaker` nos argumentos.

## Limitações da 0.0.2

- Somente Minecraft 1.7.10 e somente cliente.
- Sem camada de mapeamentos e sem hooks de renderização ou GUI.
- Sem verificação de dependências entre mods.
- Testado em Linux com Java 8; ainda não testado em launchers Android.

## Licença

Veja `License.txt`.
