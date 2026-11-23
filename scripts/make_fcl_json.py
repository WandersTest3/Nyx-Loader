import argparse
import copy
import hashlib
import json
import os
import urllib.request

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
LAUNCH_MAIN = "dev.nyx.loader.launch.NyxMain"
JAVA_MAJOR = 21
REPO_URL = "https://raw.githubusercontent.com/WandersTest3/Nyx-Loader/v{v}/maven/dev/nyx/nyx-loader/{v}/nyx-loader-{v}.jar"

CENTRAL = "https://repo1.maven.org/maven2/"
FABRIC = "https://maven.fabricmc.net/"
LEGACY_FABRIC = "https://repo.legacyfabric.net/legacyfabric/"

LIBRARIES = [
    ("org.ow2.asm:asm:9.8", CENTRAL, "dc19ecb3f7889b7860697215cae99c0f9b6f6b4b", 126113),
    ("org.ow2.asm:asm-tree:9.8", CENTRAL, "018419ca5b77a2f81097c741e7872e6ab8d2f40d", 51934),
    ("org.ow2.asm:asm-commons:9.8", CENTRAL, "36e4d212970388e5bd2c5180292012502df461bb", 73498),
    ("org.ow2.asm:asm-util:9.8", CENTRAL, "395f1c1f035258511f27bc9b2583d76e4b143f59", 94559),
    ("org.ow2.asm:asm-analysis:9.8", CENTRAL, "b9747a320844b6cb1eacd90d8ecfd260a16c01d3", 35151),
    ("net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7", FABRIC, "0cef8519a91129d0c21fa03bb9378f0e8ca810e5", 1551593),
    ("net.legacyfabric:intermediary:1.7.10", LEGACY_FABRIC, "101f7a07dd6adfd26b1e180a13235104b6d954ae", 113936),
    ("com.twelvemonkeys.imageio:imageio-webp:3.12.0", CENTRAL, "390599d525f6af2af4d94bd549411d6e1d47ea07", 79713),
    ("com.twelvemonkeys.imageio:imageio-core:3.12.0", CENTRAL, "4a57990b52fd36fe60c356a3e5fd57054a85a502", 119909),
    ("com.twelvemonkeys.imageio:imageio-metadata:3.12.0", CENTRAL, "6997264db25396a2c8fddf6520174c9a907e3403", 83615),
    ("com.twelvemonkeys.common:common-lang:3.12.0", CENTRAL, "170b88f23daa4d4d64189285ebebe4eb59d51c93", 121817),
    ("com.twelvemonkeys.common:common-io:3.12.0", CENTRAL, "1f739ad65a48fd2bc7b8c0a8bb54218c0e219c84", 92346),
    ("com.twelvemonkeys.common:common-image:3.12.0", CENTRAL, "1ffc9fb885eea7f0bdab0f6e7f780d3e41b5ad01", 79655),
]


FORGE = "https://maven.minecraftforge.net/"
MOJANG = "https://libraries.minecraft.net/"

FORGE_LIBRARIES = [
    ("net.minecraftforge:forge:1.7.10-10.13.4.1614-1.7.10:universal", FORGE, "25fd97f72beca728112256938e03e8105b1b78cc", 3018833),
    ("org.apache.commons:commons-compress:1.21", CENTRAL, "4ec95b60d4e86b5c95a0e919cb172a0af98011ef", 1018316),
    ("com.google.guava:guava:17.0", MOJANG, "9c6ef172e8de35fd8d4d8783e4821e57cdef7445", 2243036),
    ("org.apache.commons:commons-lang3:3.3.2", MOJANG, "90a3822c38ec8c996e84c16a3477ef632cbc87a3", 412739),
    ("lzma:lzma:0.0.1", MOJANG, "521616dc7487b42bef0e803bd2fa3faf668101d7", 5762),
    ("com.typesafe.akka:akka-actor_2.11:2.3.3", FORGE, "ed62e9fc709ca0f2ff1a3220daa8b70a2870078e", 2514991),
    ("com.typesafe:config:1.2.1", FORGE, "f771f71fdae3df231bcd54d5ca2d57f0bf93f467", 219554),
    ("org.scala-lang:scala-actors-migration_2.11:1.1.0", FORGE, "dfa8bc42b181d5b9f1a5dd147f8ae308b893eb6f", 58018),
    ("org.scala-lang:scala-compiler:2.11.1", FORGE, "56ea2e6c025e0821f28d73ca271218b8dd04926a", 13449765),
    ("org.scala-lang.plugins:scala-continuations-library_2.11:1.0.2", FORGE, "0e517c53a7e9acd6b1668c5a35eccbaa3bab9aac", 25365),
    ("org.scala-lang.plugins:scala-continuations-plugin_2.11.1:1.0.2", FORGE, "f361a3283452c57fa30c1ee69448995de23c60f7", 206575),
    ("org.scala-lang:scala-library:2.11.1", FORGE, "0e11da23da3eabab9f4777b9220e60d44c1aab6a", 5538130),
    ("org.scala-lang:scala-parser-combinators_2.11:1.0.1", FORGE, "f05d7345bf5a58924f2837c6c1f4d73a938e1ff0", 419701),
    ("org.scala-lang:scala-reflect:2.11.1", FORGE, "6580347e61cc7f8e802941e7fde40fa83b8badeb", 4372892),
    ("org.scala-lang:scala-swing_2.11:1.0.1", FORGE, "b1cdd92bd47b1e1837139c1c53020e86bb9112ae", 726500),
    ("org.scala-lang:scala-xml_2.11:1.0.2", FORGE, "7a80ec00aec122fba7cd4e0d4cdd87ff7e4cb6d0", 648679),
]


def maven_library(name, repo, sha1, size):
    parts = name.split(":")
    group, artifact, version = parts[0], parts[1], parts[2]
    suffix = "-" + parts[3] if len(parts) > 3 else ""
    path = "{}/{}/{}/{}-{}{}.jar".format(group.replace(".", "/"), artifact, version, artifact, version, suffix)
    return {
        "name": name,
        "downloads": {"artifact": {"path": path, "sha1": sha1, "size": size, "url": repo + path}},
    }


def fetch_json(url):
    with urllib.request.urlopen(url, timeout=60) as response:
        return json.load(response)


def nyx_library(jar, version):
    with open(jar, "rb") as handle:
        data = handle.read()
    return {
        "name": "dev.nyx:nyx-loader:" + version,
        "downloads": {
            "artifact": {
                "path": "dev/nyx/nyx-loader/{v}/nyx-loader-{v}.jar".format(v=version),
                "sha1": hashlib.sha1(data).hexdigest(),
                "size": len(data),
                "url": REPO_URL.format(v=version),
            }
        },
    }


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mc", required=True)
    parser.add_argument("--nyx-version", required=True)
    parser.add_argument("--jar", required=True)
    parser.add_argument("--out", required=True)
    parser.add_argument("--forge", action="store_true")
    args = parser.parse_args()

    manifest = fetch_json(MANIFEST)
    entry = next(v for v in manifest["versions"] if v["id"] == args.mc)
    base = fetch_json(entry["url"])

    name = "Nyx-" + args.mc
    libraries = [nyx_library(args.jar, args.nyx_version)] + [maven_library(*lib) for lib in LIBRARIES]
    if args.forge:
        libraries += [maven_library(*lib) for lib in FORGE_LIBRARIES]
    replaced = {":".join(lib["name"].split(":")[:2]) for lib in libraries}
    base_libraries = [lib for lib in base["libraries"] if ":".join(lib["name"].split(":")[:2]) not in replaced]
    arguments = base["minecraftArguments"]
    java = {"component": "java-runtime-delta", "majorVersion": JAVA_MAJOR}

    game_patch = copy.deepcopy(base)
    game_patch.update({"id": "game", "version": args.mc, "priority": 0})

    nyx_patch = {
        "id": "nyx",
        "version": args.nyx_version,
        "priority": 30000,
        "libraries": libraries,
        "mainClass": LAUNCH_MAIN,
        "minecraftArguments": arguments,
        "javaVersion": java,
        "releaseTime": base["releaseTime"],
        "time": base["time"],
        "type": base["type"],
    }

    root = copy.deepcopy(base)
    root.update(
        {
            "id": name,
            "jar": name,
            "root": True,
            "libraries": libraries + base_libraries,
            "mainClass": LAUNCH_MAIN,
            "minecraftArguments": arguments,
            "javaVersion": java,
            "patches": [game_patch, nyx_patch],
        }
    )

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as handle:
        json.dump(root, handle, indent=2)
    print("wrote", args.out)


if __name__ == "__main__":
    main()
