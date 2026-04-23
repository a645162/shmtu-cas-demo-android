from __future__ import annotations

import argparse
import re
import shutil
import urllib.request
from urllib.parse import urljoin
import zipfile
from pathlib import Path

target_path = r"shmtu_ocr/src/main/cpp/3rdparty"

LIB_TARGETS = [
    {
        "name": "ncnn",
        "owner": "Tencent",
        "repo": "ncnn",
        "asset_pattern": r"ncnn-.*-android-vulkan\.zip",
        "cmake_rel_dir": "{folder}/${{ANDROID_ABI}}/lib/cmake/ncnn",
        "cmake_var": "ncnn_DIR",
    },
    {
        "name": "opencv-mobile",
        "owner": "nihui",
        "repo": "opencv-mobile",
        "asset_pattern": r"opencv-mobile-4.*-android\.zip",
        "cmake_rel_dir": "{folder}/sdk/native/jni",
        "cmake_var": "OpenCV_DIR",
    },
]


def fetch_latest_asset_url(
    owner: str, repo: str, asset_pattern: str
) -> tuple[str, str]:
    latest_page = f"https://github.com/{owner}/{repo}/releases/latest"
    request = urllib.request.Request(
        latest_page, headers={"User-Agent": "download-lib-script"}
    )

    with urllib.request.urlopen(request) as response:
        final_page = response.geturl()

    tag_name = final_page.rstrip("/").rsplit("/", 1)[-1]
    assets_page = (
        f"https://github.com/{owner}/{repo}/releases/expanded_assets/{tag_name}"
    )
    assets_request = urllib.request.Request(
        assets_page, headers={"User-Agent": "download-lib-script"}
    )

    with urllib.request.urlopen(assets_request) as response:
        html = response.read().decode("utf-8", errors="replace")

    asset_urls = {
        urljoin(final_page, match)
        for match in re.findall(r'href="([^"]*?/releases/download/[^"]*?\.zip)"', html)
    }
    matched_assets = [
        url
        for url in sorted(asset_urls)
        if re.fullmatch(asset_pattern, url.rsplit("/", 1)[-1])
    ]

    if not matched_assets:
        raise RuntimeError(
            f"No asset matched pattern {asset_pattern!r} in {owner}/{repo} latest release {tag_name!r}."
        )

    return tag_name, matched_assets[0]


def download_file(url: str, destination: Path) -> None:
    request = urllib.request.Request(url, headers={"User-Agent": "download-lib-script"})
    with urllib.request.urlopen(request) as response, destination.open("wb") as output:
        shutil.copyfileobj(response, output)


def extract_zip(zip_path: Path, target_dir: Path) -> None:
    with zipfile.ZipFile(zip_path) as archive:
        archive.extractall(target_dir)


def ensure_local_package(url: str, target_dir: Path) -> str:
    zip_name = url.rsplit("/", 1)[-1]
    zip_path = target_dir / zip_name
    folder_name = zip_name[:-4]
    package_dir = target_dir / folder_name

    if package_dir.exists():
        print(f"skip download: directory exists -> {package_dir.name}")
        return folder_name

    if zip_path.exists():
        print(f"skip download: zip exists -> {zip_name}")
    else:
        print(f"downloading: {zip_name}")
        download_file(url, zip_path)

    print(f"extracting: {zip_name}")
    extract_zip(zip_path, target_dir)
    return folder_name


def update_cmake_paths(cmake_file: Path, cmake_updates: dict[str, str]) -> None:
    if not cmake_file.exists():
        print(f"skip cmake update: file not found -> {cmake_file}")
        return

    text = cmake_file.read_text(encoding="utf-8")
    updated_text = text

    for cmake_var, rel_dir in cmake_updates.items():
        replacement = f"set({cmake_var} ${{LIB_3RD_PARTY_DIR}}/{rel_dir})"
        updated_text, count = re.subn(
            rf"^set\({re.escape(cmake_var)}\s+[^\n]*\)$",
            replacement,
            updated_text,
            count=1,
            flags=re.MULTILINE,
        )

        if count == 0:
            raise RuntimeError(
                f"Cannot find CMake variable line to update: {cmake_var}"
            )

    if updated_text != text:
        cmake_file.write_text(updated_text, encoding="utf-8")
        print(f"updated cmake: {cmake_file}")
    else:
        print("cmake already up to date")


def main() -> None:
    parser = argparse.ArgumentParser(
        description="Download and extract the latest ncnn and opencv-mobile Android archives."
    )
    parser.add_argument(
        "--skip-download",
        action="store_true",
        help="Only print the resolved latest URLs without downloading anything.",
    )
    parser.add_argument(
        "--skip-cmake-update",
        action="store_true",
        help="Do not modify CMakeLists.txt.",
    )
    args = parser.parse_args()

    script_dir = Path(__file__).resolve().parent
    target_dir = (script_dir / target_path).resolve()
    target_dir.mkdir(parents=True, exist_ok=True)
    cmake_file = target_dir.parent / "CMakeLists.txt"
    print(f"target directory: {target_dir}")
    cmake_updates: dict[str, str] = {}

    for target in LIB_TARGETS:
        tag_name, url = fetch_latest_asset_url(
            target["owner"], target["repo"], target["asset_pattern"]
        )
        print(f"{target['name']}: {url} ({tag_name})")

        folder_name = url.rsplit("/", 1)[-1][:-4]

        if args.skip_download:
            print(f"skip download by arg: {target['name']}")
        else:
            folder_name = ensure_local_package(url, target_dir)

        cmake_updates[target["cmake_var"]] = target["cmake_rel_dir"].format(
            folder=folder_name
        )

    if not args.skip_cmake_update:
        update_cmake_paths(cmake_file, cmake_updates)


if __name__ == "__main__":
    main()
