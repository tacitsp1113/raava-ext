#!/usr/bin/env python3
# coding: utf-8

import os
import re
import logging
from urllib.parse import urlparse
from cloudscraper import CloudScraper

logging.basicConfig(level=logging.INFO, format='[%(levelname)s] %(message)s')
logger = logging.getLogger("RaavaDomainCheck")

class RaavaDomainUpdater:
    def __init__(self, base_dir="."):
        self.base_dir = base_dir
        self.session = CloudScraper()
        self.blacklist = {
            "instapro.ac",
        }

    @property
    def providers(self):
        try:
            candidates = [
                d for d in os.listdir(self.base_dir)
                if os.path.isdir(os.path.join(self.base_dir, d))
                and not d.startswith(".")
                and d not in {"gradle", "assets", "build", "raava-live", "scratch"}
                and os.path.exists(os.path.join(self.base_dir, d, "build.gradle.kts"))
            ]
            return sorted(candidates)
        except FileNotFoundError:
            return []

    def _find_kt_file(self, provider_dir):
        start = os.path.join(self.base_dir, provider_dir)
        for root, _, files in os.walk(start):
            for f in files:
                if f.endswith(".kt") and not f.endswith("Plugin.kt") and not f.endswith("Extractors.kt"):
                    return os.path.join(root, f)
        return None

    def _get_main_url(self, kt_file_path):
        try:
            with open(kt_file_path, "r", encoding="utf-8") as f:
                content = f.read()
                if m := re.search(r'override\s+var\s+mainUrl\s*=\s*["\']([^"\']+)["\']', content):
                    return m.group(1)
        except Exception:
            logger.error(f"Error al leer archivo: {kt_file_path}")
        return None

    def _update_main_url(self, kt_file_path, old_url, new_url):
        try:
            with open(kt_file_path, "r+", encoding="utf-8") as f:
                content = f.read()
                new_content, count = re.subn(
                    r'(override\s+var\s+mainUrl\s*=\s*["\'])([^"\']+)(["\'])',
                    r'\1' + new_url + r'\3',
                    content,
                    flags=re.IGNORECASE
                )
                if count == 0:
                    new_content = content.replace(old_url, new_url)

                if new_content == content:
                    return False

                f.seek(0)
                f.write(new_content)
                f.truncate()
            return True
        except Exception:
            return False

    def _update_gradle(self, gradle_path, new_url):
        try:
            new_domain = self._clean_domain(new_url)
            with open(gradle_path, "r+", encoding="utf-8") as f:
                content = f.read()

                # Incrementar versión entera para que Cloudstream detecte la actualización
                if v_match := re.search(r'(^\s*version\s*=\s*)(\d+)', content, flags=re.MULTILINE):
                    old_v = int(v_match.group(2))
                    new_v = old_v + 1
                    content = content.replace(v_match.group(0), f"{v_match.group(1)}{new_v}")
                else:
                    new_v = None

                # Actualizar el favicon en iconUrl si aplica
                clean_host = new_domain.replace("https://", "").replace("http://", "")
                content = re.sub(r'(domain=)([^&"\s]+)', r'\1' + clean_host, content)

                f.seek(0)
                f.write(content)
                f.truncate()
                return new_v
        except Exception:
            return None

    def _clean_domain(self, url, prefer_https=True):
        if not url:
            return None
        try:
            parsed = urlparse(url if "://" in url else f"http://{url}")
            scheme = "https" if prefer_https else parsed.scheme
            netloc = parsed.netloc
            if netloc.startswith("www."):
                netloc = netloc[4:]
            return f"{scheme}://{netloc}"
        except Exception:
            return None

    def update_domains(self):
        for provider in self.providers:
            kt_file = self._find_kt_file(provider)
            if not kt_file:
                continue

            main_url = self._get_main_url(kt_file)
            if not main_url:
                continue

            logger.info(f"[~] Verificando proveedor: {provider} ({main_url})")
            clean_url = self._clean_domain(main_url)
            if not clean_url:
                continue

            try:
                resp = self.session.get(clean_url, allow_redirects=True, timeout=15)
                final_url = resp.url.rstrip('/')
            except Exception as e:
                logger.warning(f"[!] Error de conexión en {clean_url}: {e}")
                continue

            new_domain = self._clean_domain(final_url)
            if not new_domain or clean_url == new_domain:
                continue

            parsed_new = urlparse(new_domain)
            if parsed_new.netloc.lower() in self.blacklist:
                logger.info(f"[-] Omitido por blacklist: {new_domain}")
                continue

            # Actualizar archivo .kt y build.gradle.kts
            if self._update_main_url(kt_file, main_url, new_domain):
                gradle_path = os.path.join(self.base_dir, provider, "build.gradle.kts")
                new_v = self._update_gradle(gradle_path, new_domain)
                logger.info(f"[✓] {provider}: {main_url} -> {new_domain} (v{new_v if new_v else '?'})")

if __name__ == "__main__":
    updater = RaavaDomainUpdater(base_dir=".")
    updater.update_domains()
