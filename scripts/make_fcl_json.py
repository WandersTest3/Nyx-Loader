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
]


def maven_library(name, repo, sha1, size):
    group, artifact, version = name.split(":")
    path = "{}/{}/{}/{}-{}.jar".format(group.replace(".", "/"), artifact, version, artifact, version)
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
    args = parser.parse_args()

    manifest = fetch_json(MANIFEST)
    entry = next(v for v in manifest["versions"] if v["id"] == args.mc)
    base = fetch_json(entry["url"])

    name = "Nyx-" + args.mc
    libraries = [nyx_library(args.jar, args.nyx_version)] + [maven_library(*lib) for lib in LIBRARIES]
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
            "libraries": libraries + base["libraries"],
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
