# Nyx Loader

Mod loader client-side para Minecraft Java Edition. Em desenvolvimento.

**Versão atual: 0.0.1** — suporta apenas o Minecraft **1.0**, com **Java 8**.

## Como funciona

O Nyx roda sobre o LaunchWrapper (`net.minecraft.launchwrapper.Launch`), que já vem na versão 1.0 do Minecraft publicada pela Mojang.

1. O launcher inicia o jogo com `--tweakClass dev.nyx.loader.NyxTweaker`.
2. O `NyxTweaker` delega ao `VanillaTweaker` (que mantém o `--gameDir` funcionando), registra o transformador de bytecode e procura mods em `<pasta do jogo>/mods/*.jar`.
3. O `NyxClassTransformer` injeta dois hooks em `net.minecraft.client.Minecraft` usando ASM:
   - fim da inicialização do jogo (`a()V`), onde os mods são inicializados;
   - início de cada tick do cliente (`k()V`).
4. Se os métodos esperados não forem encontrados (outro build do jogo), o hook não é aplicado e um aviso aparece no log.

Os nomes ofuscados acima foram verificados no `client.jar` do Minecraft 1.0 (sha1 `b679fea27f2284836202e9365e13a82552092e5d`).

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

API da 0.0.1 (`dev.nyx.api.Nyx`): `onClientTick(Runnable)`, `getGameDirectory()`, `getModsDirectory()`, `isModLoaded(String)`, `log(String)`.

Classes do LWJGL 2 (`Display`, `Keyboard`, `Mouse`, `GL11`) podem ser usadas diretamente. Classes do Minecraft são ofuscadas nesta versão e ainda não há camada de mapeamentos.

Dependências do mod (escopo `provided`): `dev.nyx:nyx-loader` e `org.lwjgl.lwjgl:lwjgl:2.9.0`. Veja `examples/`.

## Exemplos

- `examples/hello-nyx`: escreve no log e muda o título da janela.
- `examples/session-timer`: mostra o tempo de sessão no título da janela, atualizado pelo hook de tick.

## Compilar

Requer JDK 17 ou superior e Maven.

```
mvn package
bash scripts/package-release.sh 0.0.1
```

A saída fica em `dist/`. O GitHub Actions faz o mesmo a cada push e, ao enviar uma tag `v*`, publica um Release.

## Instalar no Fold Craft Launcher (FCL)

1. No FCL, instale o Minecraft **1.0** normal e abra-o uma vez.
2. Baixe `Nyx-0.0.1-install.zip` na página de Releases.
3. Extraia o zip dentro da pasta do jogo do FCL (a que contém `versions` e `libraries`), mesclando as pastas.
4. Atualize a lista de versões do FCL e selecione **Nyx-1.0**. Use Java 8.
5. Coloque os mods em `mods/` dentro da pasta do jogo (com versão isolada: `versions/Nyx-1.0/mods`). Mods de exemplo estão em `mods-examples/` no zip.
6. Procure linhas começando com `[Nyx]` no log para confirmar.

Instalação manual: o arquivo `versions/Nyx-1.0/Nyx-1.0.json` herda de `1.0`, troca o `mainClass` por `net.minecraft.launchwrapper.Launch` e acrescenta `--tweakClass dev.nyx.loader.NyxTweaker` nos argumentos, além do `nyx-loader-0.0.1.jar` como biblioteca.

## Limitações da 0.0.1

- Somente Minecraft 1.0 e somente cliente.
- Sem camada de mapeamentos e sem hooks de renderização ou GUI.
- Sem verificação de dependências entre mods.
- Testado em Linux com Java 8; ainda não testado em launchers Android.

## Licença

Veja `License.txt`.
