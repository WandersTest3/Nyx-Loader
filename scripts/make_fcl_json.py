import argparse
import copy
import hashlib
import json
import os
import urllib.request

MANIFEST = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json"
TWEAK_ARGS = " --tweakClass dev.nyx.loader.NyxTweaker"
LAUNCH_MAIN = "net.minecraft.launchwrapper.Launch"
REPO_URL = "https://github.com/WandersTest3/Nyx-Loader/releases/download/v{v}/nyx-loader-{v}.jar"

LAUNCHWRAPPER = {
    "name": "net.minecraft:launchwrapper:1.12",
    "downloads": {
        "artifact": {
            "path": "net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar",
            "sha1": "111e7bea9c968cdb3d06ef4632bf7ff0824d0f36",
            "size": 32999,
            "url": "https://libraries.minecraft.net/net/minecraft/launchwrapper/1.12/launchwrapper-1.12.jar",
        }
    },
}

ASM = {
    "name": "org.ow2.asm:asm-all:5.0.3",
    "downloads": {
        "artifact": {
            "path": "org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar",
            "sha1": "4333508b8dd8ee72aa4e39afa713b3a74579b773",
            "size": 241639,
            "url": "https://repo1.maven.org/maven2/org/ow2/asm/asm-all/5.0.3/asm-all-5.0.3.jar",
        }
    },
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
    libraries = [LAUNCHWRAPPER, ASM, nyx_library(args.jar, args.nyx_version)]
    arguments = base["minecraftArguments"] + TWEAK_ARGS

    game_patch = copy.deepcopy(base)
    game_patch.update({"id": "game", "version": args.mc, "priority": 0})

    nyx_patch = {
        "id": "nyx",
        "version": args.nyx_version,
        "priority": 30000,
        "libraries": libraries,
        "mainClass": LAUNCH_MAIN,
        "minecraftArguments": arguments,
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
            "patches": [game_patch, nyx_patch],
        }
    )

    os.makedirs(os.path.dirname(os.path.abspath(args.out)), exist_ok=True)
    with open(args.out, "w") as handle:
        json.dump(root, handle, indent=2)
    print("wrote", args.out)


if __name__ == "__main__":
    main()
